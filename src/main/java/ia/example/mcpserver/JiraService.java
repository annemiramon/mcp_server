package ia.example.mcpserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class JiraService {

    @Value("${jira.url:https://your-instance.atlassian.net}")
    private String jiraUrl;

    @Value("${jira.username:}")
    private String jiraUsername;

    @Value("${jira.api-token:}")
    private String jiraApiToken;

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JiraService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    private HttpHeaders getAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");

        if (jiraUsername != null && !jiraUsername.isEmpty() &&
                jiraApiToken != null && !jiraApiToken.isEmpty()) {
            String auth = jiraUsername + ":" + jiraApiToken;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
        }

        return headers;
    }

    @McpTool(description = "Récupérer ticket sur un projet Jira")
    public String recupererTicket(
            @McpToolParam(description = "Clé du ticket (ex: PROJ-123)", required = true) String ticketKey) {
        try {
            String url = jiraUrl + "/rest/api/3/issue/" + ticketKey;
            HttpEntity<String> entity = new HttpEntity<>(getAuthHeaders());

            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                return "Erreur: Ticket non trouvé";
            }

            JsonNode issueNode = objectMapper.readTree(response);

            StringBuilder result = new StringBuilder();
            result.append("🎫 Ticket: ").append(issueNode.get("key").asText()).append("\n");
            result.append("📋 Titre: ").append(issueNode.get("fields").get("summary").asText()).append("\n");

            JsonNode description = issueNode.get("fields").get("description");
            if (description != null && !description.isNull()) {
                result.append("📝 Description: ").append(extractText(description)).append("\n");
            }

            result.append("✅ Statut: ").append(issueNode.get("fields").get("status").get("name").asText()).append("\n");

            JsonNode assignee = issueNode.get("fields").get("assignee");
            if (assignee != null && !assignee.isNull()) {
                result.append("👤 Assigné à: ").append(assignee.get("displayName").asText());
            } else {
                result.append("👤 Assigné à: Non assigné");
            }

            return result.toString();
        } catch (Exception e) {
            return "❌ Erreur lors de la récupération du ticket: " + e.getMessage();
        }
    }

    @McpTool(description = "Créer un ticket sur un projet Jira")
    public String creerTicket(
            @McpToolParam(description = "Clé du projet Jira (ex: PROJ)", required = true) String projectKey,
            @McpToolParam(description = "Titre/Résumé du ticket", required = true) String summary,
            @McpToolParam(description = "Description du ticket", required = true) String description,
            @McpToolParam(description = "Type de problème (Bug, Task, Story, etc.)", required = false) String issueType) {
        try {
            String url = jiraUrl + "/rest/api/3/issue";

            // Déterminer le type de problème
            String type = (issueType != null && !issueType.isEmpty()) ? issueType : "Task";

            // Construire le payload JSON
            Map<String, Object> issueData = new HashMap<>();
            Map<String, Object> fields = new HashMap<>();

            fields.put("summary", summary);
            fields.put("description", createAdfDescription(description));

            // Type de problème
            Map<String, String> issueTypeMap = new HashMap<>();
            issueTypeMap.put("name", type);
            fields.put("issuetype", issueTypeMap);

            // Projet
            Map<String, String> projectMap = new HashMap<>();
            projectMap.put("key", projectKey);
            fields.put("project", projectMap);

            issueData.put("fields", fields);

            String json = objectMapper.writeValueAsString(issueData);
            HttpEntity<String> entity = new HttpEntity<>(json, getAuthHeaders());

            String response = restTemplate.postForObject(url, entity, String.class);
            if (response == null) {
                return "❌ Erreur: Réponse vide du serveur";
            }

            JsonNode responseNode = objectMapper.readTree(response);
            String newIssueKey = responseNode.get("key").asText();

            return String.format("✅ Ticket créé avec succès!\n🔑 Clé: %s\n🌐 URL: %s/browse/%s",
                    newIssueKey,
                    jiraUrl,
                    newIssueKey);
        } catch (Exception e) {
            return "❌ Erreur lors de la création du ticket: " + e.getMessage();
        }
    }

    private Map<String, Object> createAdfDescription(String description) {
        Map<String, Object> desc = new HashMap<>();
        desc.put("type", "doc");
        desc.put("version", 1);

        Map<String, Object> paragraph = new HashMap<>();
        paragraph.put("type", "paragraph");

        Map<String, Object> text = new HashMap<>();
        text.put("type", "text");
        text.put("text", description);

        paragraph.put("content", new Object[]{text});
        desc.put("content", new Object[]{paragraph});

        return desc;
    }

    private String extractText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }

        if (node.isTextual()) {
            return node.asText();
        }

        // Si c'est un objet JSON, essayer d'extraire un texte
        if (node.isObject() && node.has("content")) {
            JsonNode content = node.get("content");
            if (content.isArray() && content.size() > 0) {
                return extractText(content.get(0));
            }
        }

        return node.asText();
    }
}
