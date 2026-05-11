package ia.example.mcpserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitServiceTests {

    @Mock
    private RestTemplate restTemplate;

    private GitService gitService;

    @SystemStub
    private EnvironmentVariables variables =
            new EnvironmentVariables("TOKEN_GITHUB", "fake-token");

    @BeforeEach
    void setUp() {
        gitService = new GitService();
        gitService.restTemplate = restTemplate;
    }

    @Test
    void shouldReturnRepositoryInfoWhenRecuperarRepoSucceeds() {
        String repoResponse = """
                {
                  "full_name": "spring-projects/spring-boot",
                  "description": "Spring Boot Framework",
                  "html_url": "https://github.com/spring-projects/spring-boot",
                  "stargazers_count": 70000,
                  "forks_count": 35000,
                  "watchers_count": 70000,
                  "language": "Java",
                  "owner": {"login": "spring-projects"},
                  "default_branch": "main",
                  "private": false,
                  "topics": ["spring", "java"]
                }
                """;

        when(restTemplate.getForObject(contains("spring-projects/spring-boot"), eq(String.class)))
                .thenReturn(repoResponse);

        String result = gitService.recupererRepo("spring-projects/spring-boot");

        assertNotNull(result);
        assertTrue(result.contains("📦 Repository: spring-projects/spring-boot"));
        assertTrue(result.contains("spring-projects"));
        assertTrue(result.contains("70000"));
        assertFalse(result.contains("❌"));
    }

    @Test
    void shouldReturnErrorMessageWhenRecuperarRepoResponseIsNull() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(null);

        String result = gitService.recupererRepo("invalid/repo");

        assertNotNull(result);
        assertTrue(result.contains("❌ Erreur: Repository non trouvé"));
    }

    @Test
    void shouldReturnErrorMessageWhenRecuperarRepoThrowsException() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection failed"));

        String result = gitService.recupererRepo("spring-projects/spring-boot");

        assertNotNull(result);
        assertTrue(result.contains("❌ Erreur lors de la récupération du repository"));
    }

    @Test
    void shouldListRepositoryFilesSuccessfully() {
        String filesResponse = """
                [
                  {"type": "dir", "name": "src", "size": 0},
                  {"type": "file", "name": "README.md", "size": 5000},
                  {"type": "file", "name": "pom.xml", "size": 2000}
                ]
                """;

        when(restTemplate.getForObject(contains("spring-boot/contents"), eq(String.class)))
                .thenReturn(filesResponse);

        String result = gitService.listerFichiersRepo("spring-projects/spring-boot", null);

        assertNotNull(result);
        assertTrue(result.contains("📁 Arborescence de: spring-projects/spring-boot"));
        assertTrue(result.contains("📂 src/"));
        assertTrue(result.contains("📄 README.md"));
        assertTrue(result.contains("bytes"));
    }

    @Test
    void shouldListRepositoryFilesInSpecificPathSuccessfully() {
        String filesResponse = """
                [
                  {"type": "file", "name": "Main.java", "size": 3000},
                  {"type": "file", "name": "Utils.java", "size": 2000}
                ]
                """;

        when(restTemplate.getForObject(contains("src/main/java"), eq(String.class)))
                .thenReturn(filesResponse);

        String result = gitService.listerFichiersRepo("spring-projects/spring-boot", "src/main/java");

        assertNotNull(result);
        assertTrue(result.contains("src/main/java"));
        assertTrue(result.contains("📄 Main.java"));
    }

    @Test
    void shouldReturnErrorWhenListingFilesResponseIsNull() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(null);

        String result = gitService.listerFichiersRepo("spring-projects/spring-boot", null);

        assertNotNull(result);
        assertTrue(result.contains("❌ Erreur: Fichiers non trouvés"));
    }

    @Test
    void shouldReadFileContentSuccessfully() {
        String readmeContent = "# Spring Boot\nThis is a framework";
        String encodedContent = java.util.Base64.getEncoder().encodeToString(readmeContent.getBytes());

        String fileResponse = """
                {
                  "name": "README.md",
                  "path": "README.md",
                  "content": "%s",
                  "html_url": "https://github.com/spring-projects/spring-boot/blob/main/README.md",
                  "size": 100
                }
                """.formatted(encodedContent);

        when(restTemplate.getForObject(contains("README.md"), eq(String.class)))
                .thenReturn(fileResponse);

        String result = gitService.lireContenuFichier("spring-projects/spring-boot", "README.md");

        assertNotNull(result);
        assertTrue(result.contains("README.md"));
        assertTrue(result.contains("# Spring Boot"));
        assertTrue(result.contains("This is a framework"));
    }

    @Test
    void shouldDecodeBase64ContentWithLineBreaksSuccessfully() {
        String fileContent = "Line 1\nLine 2\nLine 3";
        String encodedContent = java.util.Base64.getEncoder().encodeToString(fileContent.getBytes());
        // Simulate GitHub returning base64 with line breaks
        String encodedWithLineBreaks = encodedContent.replaceAll("(.{76})", "$1\n");

        String fileResponse = """
                {
                  "name": "test.txt",
                  "content": "%s",
                  "html_url": "https://github.com/example/repo/blob/main/test.txt",
                  "size": 20
                }
                """.formatted(encodedWithLineBreaks);

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(fileResponse);

        String result = gitService.lireContenuFichier("example/repo", "test.txt");

        assertNotNull(result);
        assertTrue(result.contains("Line 1"));
        assertTrue(result.contains("Line 2"));
        assertTrue(result.contains("Line 3"));
    }

    @Test
    void shouldReturnErrorWhenReadingFileResponseIsNull() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(null);

        String result = gitService.lireContenuFichier("spring-projects/spring-boot", "README.md");

        assertNotNull(result);
        assertTrue(result.contains("❌ Erreur: Fichier non trouvé"));
    }

    @Test
    void shouldListRecentCommitsSuccessfully() {
        String commitsResponse = """
                [
                  {
                    "sha": "abc123def456ghi789",
                    "commit": {
                      "message": "Fix bug in parser\\nMore details",
                      "author": {"name": "John Doe", "date": "2024-05-08T10:00:00Z"}
                    }
                  },
                  {
                    "sha": "xyz987uvw654tsr321",
                    "commit": {
                      "message": "Add new feature",
                      "author": {"name": "Jane Smith", "date": "2024-05-07T15:30:00Z"}
                    }
                  }
                ]
                """;

        when(restTemplate.getForObject(contains("commits?per_page=10"), eq(String.class)))
                .thenReturn(commitsResponse);

        String result = gitService.listerCommitsRecents("spring-projects/spring-boot", null);

        assertNotNull(result);
        assertTrue(result.contains("📜 Derniers commits"));
        assertTrue(result.contains("abc123d"));
        assertTrue(result.contains("Fix bug in parser"));
        assertTrue(result.contains("John Doe"));
    }

    @Test
    void shouldListCommitsWithCustomLimitSuccessfully() {
        String commitsResponse = """
                [
                  {
                    "sha": "aabbccdd",
                    "commit": {
                      "message": "Commit 1",
                      "author": {"name": "User", "date": "2024-05-08T10:00:00Z"}
                    }
                  }
                ]
                """;

        when(restTemplate.getForObject(contains("per_page=20"), eq(String.class)))
                .thenReturn(commitsResponse);

        String result = gitService.listerCommitsRecents("spring-projects/spring-boot", "20");

        assertNotNull(result);
        assertTrue(result.contains("Commit 1"));
    }

    @Test
    void shouldUseDefaultLimitWhenProvidedLimitIsInvalid() {
        String commitsResponse = """
                [
                  {
                    "sha": "aabbccdd",
                    "commit": {
                      "message": "Commit",
                      "author": {"name": "User", "date": "2024-05-08T10:00:00Z"}
                    }
                  }
                ]
                """;

        when(restTemplate.getForObject(contains("per_page=10"), eq(String.class)))
                .thenReturn(commitsResponse);

        String result = gitService.listerCommitsRecents("spring-projects/spring-boot", "invalid");

        assertNotNull(result);
        assertTrue(result.contains("Commit"));
    }

    @Test
    void shouldReturnErrorWhenListingCommitsResponseIsNull() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(null);

        String result = gitService.listerCommitsRecents("spring-projects/spring-boot", null);

        assertNotNull(result);
        assertTrue(result.contains("❌ Erreur: Commits non trouvés"));
    }

    @Test
    void shouldListIssuesAndPullRequestsSuccessfully() {
        String issuesResponse = """
                [
                  {
                    "number": 123,
                    "title": "Bug in parser",
                    "state": "open",
                    "user": {"login": "johndoe"},
                    "pull_request": null
                  },
                  {
                    "number": 456,
                    "title": "Add feature X",
                    "state": "open",
                    "user": {"login": "janesmith"},
                    "pull_request": {"url": "..."}
                  }
                ]
                """;

        when(restTemplate.getForObject(contains("issues?state=open"), eq(String.class)))
                .thenReturn(issuesResponse);

        String result = gitService.listerIssuesEtPrs("spring-projects/spring-boot", null, null);

        assertNotNull(result);
        assertTrue(result.contains("📋 Issues et PRs"));
        assertTrue(result.contains("❓ #123: Bug in parser"));
        assertTrue(result.contains("🔀 #456: Add feature X"));
    }

    @Test
    void shouldFilterIssuesOnlySuccessfully() {
        String issuesResponse = """
                [
                  {
                    "number": 123,
                    "title": "Bug in parser",
                    "state": "open",
                    "user": {"login": "johndoe"},
                    "pull_request": null
                  }
                ]
                """;

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(issuesResponse);

        String result = gitService.listerIssuesEtPrs("spring-projects/spring-boot", "issues", "open");

        assertNotNull(result);
        assertTrue(result.contains("❓ #123"));
    }

    @Test
    void shouldFilterClosedIssuesSuccessfully() {
        String issuesResponse = """
                [
                  {
                    "number": 789,
                    "title": "Fixed issue",
                    "state": "closed",
                    "user": {"login": "developer"},
                    "pull_request": null
                  }
                ]
                """;

        when(restTemplate.getForObject(contains("state=closed"), eq(String.class)))
                .thenReturn(issuesResponse);

        String result = gitService.listerIssuesEtPrs("spring-projects/spring-boot", null, "closed");

        assertNotNull(result);
        assertTrue(result.contains("closed"));
    }

    @Test
    void shouldReturnErrorWhenListingIssuesResponseIsNull() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(null);

        String result = gitService.listerIssuesEtPrs("spring-projects/spring-boot", null, null);

        assertNotNull(result);
        assertTrue(result.contains("❌ Erreur: Issues/PRs non trouvés"));
    }

    @Test
    void shouldCreateIssueSuccessfully() {
        String createResponse = """
                {
                  "number": 999,
                  "title": "New issue",
                  "state": "open",
                  "html_url": "https://github.com/spring-projects/spring-boot/issues/999",
                  "user": {"login": "testuser"}
                }
                """;

        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(createResponse);

        String result = gitService.creerIssue("spring-projects/spring-boot", "New issue", "Issue description", null, null);

        assertNotNull(result);
        assertTrue(result.contains("✅ Issue créée avec succès"));
        assertTrue(result.contains("📋 Titre: New issue"));
        assertTrue(result.contains("#999"));
    }

    @Test
    void shouldCreateIssueWithLabelsAndAssigneesSuccessfully() {
        String createResponse = """
                {
                  "number": 1000,
                  "title": "Urgent bug",
                  "state": "open",
                  "html_url": "https://github.com/spring-projects/spring-boot/issues/1000",
                  "user": {"login": "testuser"}
                }
                """;

        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(createResponse);

        String result = gitService.creerIssue("spring-projects/spring-boot", "Urgent bug", "Critical bug", "bug,urgent", "user1,user2");

        assertNotNull(result);
        assertTrue(result.contains("✅ Issue créée avec succès"));
        assertTrue(result.contains("1000"));
    }

    @Test
    void shouldReturnErrorWhenCreatingIssueResponseIsNull() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(null);

        String result = gitService.creerIssue("spring-projects/spring-boot", "Title", "Description", null, null);

        assertNotNull(result);
        assertTrue(result.contains("❌ Erreur: Réponse vide du serveur"));
    }

    @Test
    void shouldReturnErrorWhenCreatingIssueThrowsException() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("API Error"));

        String result = gitService.creerIssue("spring-projects/spring-boot", "Title", "Description", null, null);

        assertNotNull(result);
        assertTrue(result.contains("❌ Erreur lors de la création de l'issue"));
    }
}

