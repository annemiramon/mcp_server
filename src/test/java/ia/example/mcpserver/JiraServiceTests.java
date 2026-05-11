package ia.example.mcpserver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import uk.org.webcompere.systemstubs.environment.EnvironmentVariables;
import uk.org.webcompere.systemstubs.jupiter.SystemStub;
import uk.org.webcompere.systemstubs.jupiter.SystemStubsExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith({SystemStubsExtension.class, MockitoExtension.class})
class JiraServiceTests {

    private JiraService jiraService;

    @Mock
    private RestTemplate restTemplate;

    @SystemStub
    private EnvironmentVariables variables =
            new EnvironmentVariables("TOKEN_JIRA", "fake-token", "USERNAME_JIRA", "fake-user");


    @BeforeEach
    void setUp() {
        jiraService = new JiraService();
        ReflectionTestUtils.setField(jiraService, "restTemplate", restTemplate);
    }

    @Test
    void shouldReturnTicketInfoWhenRecuperarTicketSucceeds() {
        String ticketResponse = """
                {
                  "key": "PROJ-123",
                  "fields": {
                    "summary": "Fix login bug",
                    "description": {
                      "type": "doc",
                      "content": [{"type": "paragraph", "content": [{"type": "text", "text": "Login is broken"}]}]
                    },
                    "status": {"name": "In Progress"},
                    "assignee": {"displayName": "John Doe"}
                  }
                }
                """;

        when(restTemplate.getForObject(contains("PROJ-123"), eq(String.class)))
                .thenReturn(ticketResponse);

        String result = jiraService.recupererTicket("https://test.atlassian.net", "PROJ-123");

        assertNotNull(result);
        assertTrue(result.contains("🎫 Ticket: PROJ-123"));
        assertTrue(result.contains("📋 Titre: Fix login bug"));
        assertTrue(result.contains("✅ Statut: In Progress"));
        assertTrue(result.contains("👤 Assigné à: John Doe"));
    }

    @Test
    void shouldReturnTicketWithoutAssigneeWhenAssigneeIsNull() {
        String ticketResponse = """
                {
                  "key": "PROJ-456",
                  "fields": {
                    "summary": "New feature",
                    "description": null,
                    "status": {"name": "To Do"},
                    "assignee": null
                  }
                }
                """;

        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(ticketResponse);

        String result = jiraService.recupererTicket("https://test.atlassian.net", "PROJ-456");

        assertNotNull(result);
        assertTrue(result.contains("PROJ-456"));
        assertTrue(result.contains("Non assigné"));
    }

    @Test
    void shouldReturnErrorWhenRecuperarTicketResponseIsNull() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenReturn(null);

        String result = jiraService.recupererTicket("https://test.atlassian.net", "INVALID-999");

        assertNotNull(result);
        assertTrue(result.contains("Erreur: Ticket non trouvé"));
    }

    @Test
    void shouldReturnErrorWhenRecuperarTicketThrowsException() {
        when(restTemplate.getForObject(anyString(), eq(String.class)))
                .thenThrow(new RuntimeException("Connection failed"));

        String result = jiraService.recupererTicket("https://test.atlassian.net", "PROJ-123");

        assertNotNull(result);
        assertTrue(result.contains("❌ Erreur lors de la récupération du ticket"));
    }

    @Test
    void shouldCreateTicketSuccessfully() {
        String createResponse = """
                {
                  "key": "PROJ-999",
                  "id": "10001"
                }
                """;

        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(createResponse);

        String result = jiraService.creerTicket("https://test.atlassian.net", "PROJ", "New bug", "Critical issue", "Bug");

        assertNotNull(result);
        assertTrue(result.contains("✅ Ticket créé avec succès"));
        assertTrue(result.contains("PROJ-999"));
        assertTrue(result.contains("https://test.atlassian.net/browse/PROJ-999"));
    }

    @Test
    void shouldCreateTicketWithDefaultTypeWhenIssueTypeIsNull() {
        String createResponse = """
                {
                  "key": "PROJ-1000",
                  "id": "10002"
                }
                """;

        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(createResponse);

        String result = jiraService.creerTicket("https://test.atlassian.net", "PROJ", "New task", "Some description", null);

        assertNotNull(result);
        assertTrue(result.contains("✅ Ticket créé avec succès"));
        assertTrue(result.contains("PROJ-1000"));
    }

    @Test
    void shouldReturnErrorWhenCreatingTicketResponseIsNull() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenReturn(null);

        String result = jiraService.creerTicket("https://test.atlassian.net", "PROJ", "Title", "Description", null);

        assertNotNull(result);
        assertTrue(result.contains("❌ Erreur: Réponse vide du serveur"));
    }

    @Test
    void shouldReturnErrorWhenCreatingTicketThrowsException() {
        when(restTemplate.postForObject(anyString(), any(), eq(String.class)))
                .thenThrow(new RuntimeException("API Error"));

        String result = jiraService.creerTicket("https://test.atlassian.net", "PROJ", "Title", "Description", null);

        assertNotNull(result);
        assertTrue(result.contains("❌ Erreur lors de la création du ticket"));
    }
}

