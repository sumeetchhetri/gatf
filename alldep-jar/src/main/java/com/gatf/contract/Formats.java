/*
    Copyright 2013-2026, Sumeet Chhetri

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.
*/
package com.gatf.contract;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.regex.Pattern;

/** OA2 — string {@code format} checks for the common formats; unknown formats are not asserted (JSON Schema rule). */
final class Formats {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern UUID = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");
    private static final Pattern IPV4 = Pattern.compile("^((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)$");
    private static final Pattern HOST = Pattern.compile("^(?=.{1,253}$)([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)(\\.[A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?)*$");
    private static final Pattern DURATION = Pattern.compile("^P(?!$)(\\d+Y)?(\\d+M)?(\\d+W)?(\\d+D)?(T(?=\\d)(\\d+H)?(\\d+M)?(\\d+(\\.\\d+)?S)?)?$");

    private Formats() {}

    static boolean valid(String format, String s) {
        try {
            switch (format) {
                case "date-time": OffsetDateTime.parse(s.replace(' ', 'T').replace('t', 'T').replace('z', 'Z')); return true;
                case "date": LocalDate.parse(s); return true;
                case "time": OffsetTime.parse(s.replace('z', 'Z')); return true;
                case "email": case "idn-email": return EMAIL.matcher(s).matches();
                case "uuid": return UUID.matcher(s).matches();
                case "ipv4": return IPV4.matcher(s).matches();
                case "ipv6": return s.indexOf(':') >= 0 && java.net.InetAddress.getByName("[" + s + "]") != null && !IPV4.matcher(s).matches();
                case "hostname": return HOST.matcher(s).matches();
                case "uri": { URI u = new URI(s); return u.isAbsolute(); }
                case "uri-reference": case "url": new URI(s); return true;
                case "byte": Base64.getDecoder().decode(s); return true;
                case "duration": return DURATION.matcher(s).matches();
                case "regex": Pattern.compile(s); return true;
                default: return true;
            }
        } catch (DateTimeParseException | IllegalArgumentException | java.net.URISyntaxException | java.net.UnknownHostException e) {
            return false;
        }
    }
}
