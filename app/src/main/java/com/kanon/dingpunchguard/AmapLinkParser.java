package com.kanon.dingpunchguard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class AmapLinkParser {
    private static final int MAX_REDIRECTS = 5;
    private static final Pattern URL_PATTERN = Pattern.compile("(?:https?|androidamap)://[^\\s\"'<>，。；、）)]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern AMAP_URL_PATTERN = Pattern.compile("(?:https?://(?:www\\.)?(?:amap\\.com|uri\\.amap\\.com)|androidamap://)[^\\s\"'<>]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern P_PARAM_PATTERN = Pattern.compile("(?:[?&])p=([^&#]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern POSITION_PARAM_PATTERN = Pattern.compile("(?:[?&])position=([^&#]+)", Pattern.CASE_INSENSITIVE);

    private AmapLinkParser() {
    }

    static Result parse(String rawInput) throws IOException {
        String input = normalizeInput(rawInput);
        String resolved = input;

        if (needsNetworkResolve(input)) {
            resolved = resolveRedirect(input);
        }

        Result result = parseResolvedUrl(resolved);
        result.resolvedUrl = resolved;
        return result;
    }

    private static String normalizeInput(String rawInput) {
        String input = rawInput == null ? "" : rawInput.trim();
        if (input.isEmpty()) {
            throw new IllegalArgumentException("请输入高德分享链接");
        }

        Matcher matcher = URL_PATTERN.matcher(input);
        if (matcher.find()) {
            input = matcher.group();
        }

        if (!input.contains("://") && (
                input.startsWith("surl.amap.com")
                        || input.startsWith("amap.com")
                        || input.startsWith("www.amap.com")
                        || input.startsWith("uri.amap.com"))) {
            input = "https://" + input;
        }
        return input;
    }

    private static boolean needsNetworkResolve(String input) {
        String lower = input.toLowerCase(Locale.ROOT);
        return lower.contains("surl.amap.com") || (!containsSupportedCoordinateParam(input) && lower.startsWith("http"));
    }

    private static String resolveRedirect(String input) throws IOException {
        String current = input;
        for (int i = 0; i < MAX_REDIRECTS; i++) {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(current);
                connection = (HttpURLConnection) url.openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(8_000);
                connection.setReadTimeout(8_000);
                connection.setRequestProperty("User-Agent", "CheckinReminder/0.2.6");
                int code = connection.getResponseCode();
                String location = connection.getHeaderField("Location");
                if (code >= 300 && code < 400 && location != null && !location.isBlank()) {
                    String next = new URL(url, location).toString();
                    if (containsSupportedCoordinateParam(next)) {
                        return next;
                    }
                    current = next;
                    continue;
                }

                String finalUrl = connection.getURL().toString();
                if (finalUrl.contains("amap.com") && containsSupportedCoordinateParam(finalUrl)) {
                    return finalUrl;
                }

                String body = readBody(connection);
                String candidate = extractAmapUrl(body);
                if (candidate != null) {
                    return candidate;
                }
                return current;
            } finally {
                if (connection != null) {
                    connection.disconnect();
                }
            }
        }
        return current;
    }

    private static boolean containsPParam(String value) {
        return value != null && P_PARAM_PATTERN.matcher(value).find();
    }

    private static boolean containsPositionParam(String value) {
        return value != null && POSITION_PARAM_PATTERN.matcher(value).find();
    }

    private static boolean containsSupportedCoordinateParam(String value) {
        return containsPParam(value)
                || containsPositionParam(value)
                || (queryParameter(value, "lat") != null && queryParameter(value, "lon") != null);
    }

    private static String readBody(HttpURLConnection connection) {
        InputStream stream = null;
        try {
            stream = connection.getInputStream();
        } catch (IOException e) {
            stream = connection.getErrorStream();
        }
        if (stream == null) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null && builder.length() < 200_000) {
                builder.append(line).append('\n');
            }
        } catch (IOException ignored) {
            return "";
        }
        return builder.toString();
    }

    private static String extractAmapUrl(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        String normalized = body
                .replace("\\/", "/")
                .replace("&amp;", "&");
        Matcher matcher = AMAP_URL_PATTERN.matcher(normalized);
        if (matcher.find()) {
            return matcher.group();
        }
        return null;
    }

    private static Result parseResolvedUrl(String url) {
        String decoded = decode(url);
        String p = queryParameter(decoded, "p");
        if (p != null && !p.isBlank()) {
            return parsePParam(p);
        }

        String position = queryParameter(decoded, "position");
        if (position != null && !position.isBlank()) {
            return parsePositionParam(position, firstNonBlank(
                    queryParameter(decoded, "name"),
                    queryParameter(decoded, "poiname"),
                    queryParameter(decoded, "title")
            ));
        }

        String lat = queryParameter(decoded, "lat");
        String lon = queryParameter(decoded, "lon");
        if (lat != null && lon != null) {
            String name = firstNonBlank(
                    queryParameter(decoded, "poiname"),
                    queryParameter(decoded, "name"),
                    queryParameter(decoded, "title")
            );
            return new Result(name == null ? "公司" : name, parseCoordinate(lat, "纬度"), parseCoordinate(lon, "经度"));
        }

        throw new IllegalArgumentException("没有在链接中找到高德坐标，请从高德地点页分享位置");
    }

    private static Result parsePParam(String p) {
        String[] parts = p.split(",");
        if (parts.length < 3) {
            throw new IllegalArgumentException("高德 p 参数格式不完整");
        }

        double lat = parseCoordinate(parts[1], "纬度");
        double lon = parseCoordinate(parts[2], "经度");
        String name = parts.length >= 4 && !parts[3].isBlank() ? parts[3] : "公司";
        return new Result(name, lat, lon);
    }

    private static Result parsePositionParam(String position, String rawName) {
        String[] parts = position.split(",");
        if (parts.length < 2) {
            throw new IllegalArgumentException("高德 position 参数格式不完整");
        }

        double lon = parseCoordinate(parts[0], "经度");
        double lat = parseCoordinate(parts[1], "纬度");
        String name = rawName == null || rawName.isBlank() ? "公司" : rawName;
        return new Result(name, lat, lon);
    }

    private static String queryParameter(String url, String key) {
        if (url == null || key == null) {
            return null;
        }

        Matcher matcher = P_PARAM_PATTERN.matcher(url);
        if ("p".equals(key) && matcher.find()) {
            return decode(matcher.group(1));
        }

        int question = url.indexOf('?');
        if (question < 0 || question == url.length() - 1) {
            return null;
        }
        int hash = url.indexOf('#', question + 1);
        String query = hash >= 0 ? url.substring(question + 1, hash) : url.substring(question + 1);
        for (String item : query.split("&")) {
            int eq = item.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String name = decode(item.substring(0, eq));
            if (key.equals(name)) {
                return decode(item.substring(eq + 1));
            }
        }
        return null;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new IllegalArgumentException("链接编码解析失败", e);
        }
    }

    private static double parseCoordinate(String value, String label) {
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException(label + "不是有效数字：" + value);
        }
    }

    static final class Result {
        final String name;
        final double lat;
        final double lon;
        String resolvedUrl;

        Result(String name, double lat, double lon) {
            this.name = name;
            this.lat = lat;
            this.lon = lon;
        }
    }
}
