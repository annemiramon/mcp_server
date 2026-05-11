package ia.example.mcpserver;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
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

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public JiraService() {
        this.restTemplate = new RestTemplate();
    }

    private HttpHeaders getAuthHeaders() {
        String token = System.getenv("TOKEN_JIRA");
        String username = System.getenv("USERNAME_JIRA");
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Accept", "application/json");

        if (username != null && !username.isEmpty() &&
                token != null && !token.isEmpty()) {
            String auth = username + ":" + token;
            String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());
            headers.set("Authorization", "Basic " + encodedAuth);
        }

        return headers;
    }

    @McpTool(name = "recupererTicket", description = "Récupérer ticket sur un projet Jira")
    public String recupererTicket(
            @McpToolParam(description = "Url du Jira") String urlJira,
            @McpToolParam(description = "Clé du ticket (ex: PROJ-123)") String ticketKey) {
        try {
            String url = urlJira + "/rest/api/3/issue/" + ticketKey;

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

    @McpTool(name = "creerTicket", description = "Créer un ticket sur un projet Jira")
    public String creerTicket(
            @McpToolParam(description = "Url du Jira") String urlJira,
            @McpToolParam(description = "Clé du projet Jira (ex: PROJ)") String projectKey,
            @McpToolParam(description = "Titre/Résumé du ticket") String summary,
            @McpToolParam(description = "Description du ticket") String description,
            @McpToolParam(description = "Type de problème (Bug, Task, Story, etc.)", required = false) String issueType) {
        try {
            String url = urlJira + "/rest/api/3/issue";

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
                    urlJira,
                    newIssueKey);
        } catch (Exception e) {
            return "❌ Erreur lors de la création du ticket: " + e.getMessage();
        }
    }

    @McpTool(name = "listerTickets", description = "Récupérer une liste de tickets d'un projet Jira")
    public String listerTickets(
            @McpToolParam(description = "Url du Jira") String urlJira,
            @McpToolParam(description = "Clé du projet Jira (ex: PROJ)") String projectKey,
            @McpToolParam(description = "Statut optionnel (To Do, In Progress, Done, etc.)", required = false) String status,
            @McpToolParam(description = "Nombre maximum de tickets à retourner (défaut: 50)", required = false) String maxResults) {
        try {
            // Construire la requête JQL
            StringBuilder jql = new StringBuilder();
            jql.append("project = ").append(projectKey);

            if (status != null && !status.isEmpty()) {
                jql.append(" AND status = \"").append(status).append("\"");
            }

            jql.append(" ORDER BY created DESC");

            // Déterminer le nombre max de résultats
            int limit = 50;
            if (maxResults != null && !maxResults.isEmpty()) {
                try {
                    limit = Integer.parseInt(maxResults);
                    if (limit > 100) limit = 100; // Limiter à 100 max
                } catch (NumberFormatException ignored) {
                    limit = 50;
                }
            }

            // URI encode la requête JQL
            String encodedJql = java.net.URLEncoder.encode(jql.toString(), java.nio.charset.StandardCharsets.UTF_8);
            String url = urlJira + "/rest/api/3/search?jql=" + encodedJql + "&maxResults=" + limit;

            String response = restTemplate.getForObject(url, String.class);

            if (response == null) {
                return "❌ Erreur: Réponse vide du serveur";
            }

            JsonNode responseNode = objectMapper.readTree(response);
            JsonNode issues = responseNode.get("issues");

            if (issues == null || !issues.isArray() || issues.size() == 0) {
                return "📭 Aucun ticket trouvé pour le projet: " + projectKey +
                        (status != null && !status.isEmpty() ? " avec le statut: " + status : "");
            }

            StringBuilder result = new StringBuilder();
            result.append("📋 Liste des tickets du projet ").append(projectKey);
            if (status != null && !status.isEmpty()) {
                result.append(" (Statut: ").append(status).append(")");
            }
            result.append("\n");
            result.append("═════════════════════════════════════════\n\n");

            int count = 0;
            for (JsonNode issue : issues) {
                count++;
                String key = issue.get("key").asText();
                String summary = issue.get("fields").get("summary").asText();
                String issueStatus = issue.get("fields").get("status").get("name").asText();
                String issueType = issue.get("fields").get("issuetype").get("name").asText();
                String priority = issue.get("fields").get("priority") != null && !issue.get("fields").get("priority").isNull()
                        ? issue.get("fields").get("priority").get("name").asText()
                        : "Non définie";

                JsonNode assignee = issue.get("fields").get("assignee");
                String assignedTo = (assignee != null && !assignee.isNull())
                        ? assignee.get("displayName").asText()
                        : "Non assigné";

                result.append(count).append(". 🔑 ").append(key).append("\n");
                result.append("   📝 ").append(summary).append("\n");
                result.append("   📌 Type: ").append(issueType).append(" | ✅ Statut: ").append(issueStatus).append("\n");
                result.append("   🎯 Priorité: ").append(priority).append(" | 👤 Assigné à: ").append(assignedTo).append("\n\n");
            }

            result.append("═════════════════════════════════════════\n");
            result.append("📊 Total: ").append(issues.size()).append(" ticket(s) trouvé(s)");

            return result.toString();
        } catch (Exception e) {
            return "❌ Erreur lors de la récupération des tickets: " + e.getMessage();
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
            if (content.isArray() && !content.isEmpty()) {
                return extractText(content.get(0));
            }
        }

        return node.asText();
    }
}
