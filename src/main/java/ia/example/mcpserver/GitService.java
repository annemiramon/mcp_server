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
public class GitService {

    protected RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GitService() {
        this.restTemplate = new RestTemplate();
    }

    @McpTool(name = "recupererRepo", description = "Récupérer les informations d'un repository GitHub")
    public String recupererRepo(
            @McpToolParam(description = "Nom du repository (ex: spring-projects/spring-boot)") String repoName) {
        try {
            String url = "https://api.github.com/repos/" + repoName;
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/vnd.github.v3+json");
            headers.set("User-Agent", "MCP-Server/1.0");

            String response = restTemplate.getForObject(url, String.class);

            if (response == null) {
                return "❌ Erreur: Repository non trouvé";
            }

            JsonNode repoNode = objectMapper.readTree(response);

            StringBuilder result = new StringBuilder();
            result.append("📦 Repository: ").append(repoNode.get("full_name").asText()).append("\n");
            result.append("📝 Description: ").append(repoNode.get("description").asText()).append("\n");
            result.append("🌐 URL: ").append(repoNode.get("html_url").asText()).append("\n");
            result.append("⭐ Stars: ").append(repoNode.get("stargazers_count").asInt()).append("\n");
            result.append("🔄 Forks: ").append(repoNode.get("forks_count").asInt()).append("\n");
            result.append("👀 Watchers: ").append(repoNode.get("watchers_count").asInt()).append("\n");
            result.append("📚 Langage: ").append(repoNode.get("language").asText()).append("\n");
            result.append("👤 Propriétaire: ").append(repoNode.get("owner").get("login").asText()).append("\n");
            result.append("📁 Branche par défaut: ").append(repoNode.get("default_branch").asText()).append("\n");
            result.append("🔓 Public: ").append(repoNode.get("private").asBoolean() ? "Non" : "Oui").append("\n");

            if (!repoNode.get("topics").isEmpty()) {
                result.append("🏷️ Topics: ").append(repoNode.get("topics").toString()).append("\n");
            }

            return result.toString();
        } catch (Exception e) {
            return "❌ Erreur lors de la récupération du repository: " + e.getMessage();
        }
    }

    @McpTool(name = "listerFichiersRepo", description = "Lister les fichiers et l'arborescence d'un repository GitHub")
    public String listerFichiersRepo(
            @McpToolParam(description = "Nom du repository (ex: spring-projects/spring-boot)") String repoName,
            @McpToolParam(description = "Chemin du répertoire à explorer (optionnel, ex: src/main)", required = false) String path) {
        try {
            String urlPath = path != null && !path.isEmpty() ? "/" + path : "";
            String url = "https://api.github.com/repos/" + repoName + "/contents" + urlPath;

            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                return "❌ Erreur: Fichiers non trouvés";
            }

            JsonNode files = objectMapper.readTree(response);
            StringBuilder result = new StringBuilder();
            result.append("📁 Arborescence de: ").append(repoName);
            if (path != null && !path.isEmpty()) {
                result.append("/").append(path);
            }
            result.append("\n\n");

            if (files.isArray()) {
                for (JsonNode file : files) {
                    String type = file.get("type").asText();
                    String name = file.get("name").asText();
                    String size = file.get("size").asInt() + " bytes";

                    if ("dir".equals(type)) {
                        result.append("📂 ").append(name).append("/\n");
                    } else {
                        result.append("📄 ").append(name).append(" (").append(size).append(")\n");
                    }
                }
            }

            return result.toString();
        } catch (Exception e) {
            return "❌ Erreur lors de la récupération des fichiers: " + e.getMessage();
        }
    }

    @McpTool(name = "lireContenuFichier", description = "Lire le contenu d'un fichier spécifique dans un repository GitHub")
    public String lireContenuFichier(
            @McpToolParam(description = "Nom du repository (ex: spring-projects/spring-boot)") String repoName,
            @McpToolParam(description = "Chemin du fichier (ex: README.md ou src/Main.java)") String filePath) {
        try {
            String url = "https://api.github.com/repos/" + repoName + "/contents/" + filePath;

            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                return "❌ Erreur: Fichier non trouvé";
            }

            JsonNode fileNode = objectMapper.readTree(response);
            String content = fileNode.get("content").asText();

            // Nettoie la chaîne base64 en enlevant les sauts de ligne et espaces
            String cleanedContent = content.replaceAll("\\s", "");

            // Décode le contenu en base64
            byte[] decodedBytes = Base64.getDecoder().decode(cleanedContent);
            String decodedContent = new String(decodedBytes);

            StringBuilder result = new StringBuilder();
            result.append("📄 Fichier: ").append(filePath).append("\n");
            result.append("🔗 URL: ").append(fileNode.get("html_url").asText()).append("\n");
            result.append("📊 Taille: ").append(fileNode.get("size").asInt()).append(" bytes\n");
            result.append("\n--- Contenu ---\n\n");
            result.append(decodedContent);

            return result.toString();
        } catch (Exception e) {
            return "❌ Erreur lors de la lecture du fichier: " + e.getMessage();
        }
    }

    @McpTool(name = "listerCommitsRecents", description = "Lister les commits récents d'un repository GitHub")
    public String listerCommitsRecents(
            @McpToolParam(description = "Nom du repository (ex: spring-projects/spring-boot)") String repoName,
            @McpToolParam(description = "Nombre de commits à récupérer (défaut: 10)", required = false) String limit) {
        try {
            int commitsLimit = 10;
            if (limit != null && !limit.isEmpty()) {
                try {
                    commitsLimit = Integer.parseInt(limit);
                } catch (NumberFormatException ignored) {
                    // Conserve la valeur par défaut de 10
                }
            }

            String url = "https://api.github.com/repos/" + repoName + "/commits?per_page=" + commitsLimit;

            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                return "❌ Erreur: Commits non trouvés";
            }

            JsonNode commits = objectMapper.readTree(response);
            StringBuilder result = new StringBuilder();
            result.append("📜 Derniers commits de: ").append(repoName).append("\n\n");

            if (commits.isArray()) {
                int count = 0;
                for (JsonNode commit : commits) {
                    if (count++ >= commitsLimit) break;

                    String sha = commit.get("sha").asText().substring(0, 7);
                    String message = commit.get("commit").get("message").asText().split("\n")[0];
                    String author = commit.get("commit").get("author").get("name").asText();
                    String date = commit.get("commit").get("author").get("date").asText();

                    result.append("🔹 [").append(sha).append("] ").append(message).append("\n");
                    result.append("   👤 ").append(author).append(" - ").append(date).append("\n\n");
                }
            }

            return result.toString();
        } catch (Exception e) {
            return "❌ Erreur lors de la récupération des commits: " + e.getMessage();
        }
    }

    @McpTool(name = "listerIssuesEtPrs", description = "Lister les issues et pull requests d'un repository GitHub")
    public String listerIssuesEtPrs(
            @McpToolParam(description = "Nom du repository (ex: spring-projects/spring-boot)") String repoName,
            @McpToolParam(description = "Type à récupérer: 'issues', 'pulls' ou 'all' (défaut: all)", required = false) String type,
            @McpToolParam(description = "Statut: 'open', 'closed' ou 'all' (défaut: open)", required = false) String status) {
        try {
            String issueType = (type == null || type.isEmpty()) ? "all" : type;
            String issueStatus = (status == null || status.isEmpty()) ? "open" : status;

            String url = "https://api.github.com/repos/" + repoName + "/issues?state=" + issueStatus + "&per_page=20";

            String response = restTemplate.getForObject(url, String.class);
            if (response == null) {
                return "❌ Erreur: Issues/PRs non trouvés";
            }

            JsonNode issues = objectMapper.readTree(response);
            StringBuilder result = new StringBuilder();
            result.append("📋 Issues et PRs de: ").append(repoName).append(" [").append(issueStatus).append("]\n\n");

            if (issues.isArray()) {
                for (JsonNode issue : issues) {
                    boolean isPull = issue.has("pull_request") && !issue.get("pull_request").isNull();
                    String icon = isPull ? "🔀" : "❓";

                    if ("issues".equals(issueType) && isPull) continue;
                    if ("pulls".equals(issueType) && !isPull) continue;

                    String number = issue.get("number").asText();
                    String title = issue.get("title").asText();
                    String state = issue.get("state").asText();
                    String author = issue.get("user").get("login").asText();

                    result.append(icon).append(" #").append(number).append(": ").append(title).append("\n");
                    result.append("   👤 ").append(author).append(" - Statut: ").append(state).append("\n\n");
                }
            }

            return result.toString();
        } catch (Exception e) {
            return "❌ Erreur lors de la récupération des issues/PRs: " + e.getMessage();
        }
    }

    @McpTool(name = "creerIssue", description = "Créer une issue sur un repository GitHub")
    public String creerIssue(
            @McpToolParam(description = "Nom du repository (ex: spring-projects/spring-boot)") String repoName,
            @McpToolParam(description = "Titre de l'issue") String title,
            @McpToolParam(description = "Description de l'issue") String body,
            @McpToolParam(description = "Labels séparés par des virgules (optionnel, ex: bug,urgent)", required = false) String labels,
            @McpToolParam(description = "Usernames à assigner séparés par des virgules (optionnel, ex: user1,user2)", required = false) String assignees) {
        try {
            String url = "https://api.github.com/repos/" + repoName + "/issues";

            // Construire le payload JSON
            Map<String, Object> issueData = new HashMap<>();
            issueData.put("title", title);
            issueData.put("body", body);

            // Ajouter les labels si fourni
            if (labels != null && !labels.isEmpty()) {
                String[] labelArray = labels.split(",");
                for (int i = 0; i < labelArray.length; i++) {
                    labelArray[i] = labelArray[i].trim();
                }
                issueData.put("labels", labelArray);
            }

            // Ajouter les assignés si fourni
            if (assignees != null && !assignees.isEmpty()) {
                String[] assigneeArray = assignees.split(",");
                for (int i = 0; i < assigneeArray.length; i++) {
                    assigneeArray[i] = assigneeArray[i].trim();
                }
                issueData.put("assignees", assigneeArray);
            }

            String json = new ObjectMapper().writeValueAsString(issueData);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/vnd.github.v3+json");
            headers.set("User-Agent", "MCP-Server/1.0");
            headers.set("Authorization", "Bearer " + System.getenv("TOKEN_GITHUB"));
            String response = restTemplate.postForObject(url, new HttpEntity<>(json, headers), String.class);

            if (response == null) {
                return "❌ Erreur: Réponse vide du serveur";
            }

            JsonNode responseNode = objectMapper.readTree(response);
            int issueNumber = responseNode.get("number").asInt();
            String htmlUrl = responseNode.get("html_url").asText();

            StringBuilder result = new StringBuilder();
            result.append("✅ Issue créée avec succès!\n");
            result.append("🔑 Numéro: #").append(issueNumber).append("\n");
            result.append("📋 Titre: ").append(responseNode.get("title").asText()).append("\n");
            result.append("🌐 URL: ").append(htmlUrl).append("\n");
            result.append("👤 Créée par: ").append(responseNode.get("user").get("login").asText()).append("\n");
            result.append("📊 Statut: ").append(responseNode.get("state").asText());

            return result.toString();
        } catch (Exception e) {
            return "❌ Erreur lors de la création de l'issue: " + e.getMessage();
        }
    }
}
