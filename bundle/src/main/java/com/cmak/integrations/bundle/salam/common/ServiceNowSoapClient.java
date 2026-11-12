package com.cmak.integrations.bundle.salam.common;

import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One SOAP client for the SALAM ServiceNow Import Set web service
 * (u_incident_import.do). All four operations (Assigned, In-Progress,
 * Resolved, WorkInfo) go to the SAME endpoint and the SAME WSDL; they differ
 * only in the field values sent (see {@link #insert}).
 *
 * Auth is HTTP Basic (ServiceNow standard), NOT the old Remedy
 * AuthenticationInfo SOAP header.
 */
public final class ServiceNowSoapClient {

    /** Namespace of the import-set web service, taken from the WSDL targetNamespace. */
    public static final String NAMESPACE = "http://www.service-now.com/u_incident_import";

    private ServiceNowSoapClient() { }

    /**
     * Sends one import-set insert and returns the ServiceNow result
     * (sys_id / display_value / status / status_message...).
     *
     * @param fields ordered map of ServiceNow column name (u_xxx) -> value
     */
    public static Map<String, String> insert(String endpointUrl, String user, String password,
                                             Map<String, String> fields, int timeoutMs) throws Exception {
        if (endpointUrl == null || endpointUrl.isBlank()) {
            throw new IllegalStateException("ServiceNow endpoint URL is not configured");
        }

        String envelope = buildEnvelope(fields);
        String basic = Base64.getEncoder().encodeToString(
                (nz(user) + ":" + nz(password)).getBytes(StandardCharsets.UTF_8));

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl.trim()))
                .timeout(Duration.ofMillis(timeoutMs))
                .header("Authorization", "Basic " + basic)
                .header("Content-Type", "text/xml; charset=UTF-8")
                .header("SOAPAction", NAMESPACE + "/insert")
                .POST(HttpRequest.BodyPublishers.ofString(envelope, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return parseResponse(response.statusCode(), response.body());
    }

    static String buildEnvelope(Map<String, String> fields) {
        StringBuilder sb = new StringBuilder();
        sb.append("<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
          .append("xmlns:u=\"").append(NAMESPACE).append("\">\n")
          .append("  <soapenv:Header/>\n")
          .append("  <soapenv:Body>\n")
          .append("    <u:insert>\n");
        for (Map.Entry<String, String> e : fields.entrySet()) {
            sb.append("      <u:").append(e.getKey()).append(">")
              .append(escapeXml(e.getValue()))
              .append("</u:").append(e.getKey()).append(">\n");
        }
        sb.append("    </u:insert>\n")
          .append("  </soapenv:Body>\n")
          .append("</soapenv:Envelope>");
        return sb.toString();
    }

    static Map<String, String> parseResponse(int httpStatus, String raw) throws Exception {
        if (raw == null || raw.isBlank()) {
            throw new IllegalStateException("ServiceNow returned an empty response (HTTP " + httpStatus + ")");
        }

        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        f.setNamespaceAware(true);
        // harden against XXE
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document doc;
        try {
            doc = f.newDocumentBuilder().parse(new ByteArrayInputStream(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            // e.g. 401 HTML page
            throw new IllegalStateException("ServiceNow returned HTTP " + httpStatus
                    + " with a non-XML body: " + abbreviate(raw), ex);
        }

        String fault = firstText(doc, "faultstring");
        if (fault != null) {
            throw new IllegalStateException("ServiceNow SOAP fault: " + fault);
        }
        if (httpStatus >= 300) {
            throw new IllegalStateException("ServiceNow returned HTTP " + httpStatus + ": " + abbreviate(raw));
        }

        NodeList list = doc.getElementsByTagNameNS("*", "insertResponse");
        if (list.getLength() == 0) {
            throw new IllegalStateException("Unexpected ServiceNow response shape: " + abbreviate(raw));
        }

        Map<String, String> result = new LinkedHashMap<>();
        for (Node n = list.item(0).getFirstChild(); n != null; n = n.getNextSibling()) {
            if (n.getNodeType() == Node.ELEMENT_NODE) {
                result.put(n.getLocalName() != null ? n.getLocalName() : n.getNodeName(), n.getTextContent());
            }
        }

        String status = result.get("status");
        // "skipped" = transform found no target incident for the key (u_correlation_display)
        if ("error".equalsIgnoreCase(status) || "ignored".equalsIgnoreCase(status)
                || "skipped".equalsIgnoreCase(status)) {
            throw new IllegalStateException("ServiceNow did not apply the record (status=" + status
                    + "): " + nz(result.get("status_message")) + " " + nz(result.get("error_message")));
        }
        return result;
    }

    /** Value the Innovation Studio process gets back: ticket number if present, else sys_id. */
    public static String summarize(Map<String, String> result) {
        String v = result.get("display_value");
        if (v == null || v.isBlank()) v = result.get("sys_id");
        return v;
    }

    private static String firstText(Document doc, String localName) {
        NodeList l = doc.getElementsByTagNameNS("*", localName);
        if (l.getLength() == 0) l = doc.getElementsByTagName(localName);
        return l.getLength() == 0 ? null : l.item(0).getTextContent();
    }

    private static String abbreviate(String s) {
        return s.length() > 500 ? s.substring(0, 500) + "..." : s;
    }

    private static String nz(String s) { return s == null ? "" : s; }

    public static String escapeXml(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&apos;");
    }
}
