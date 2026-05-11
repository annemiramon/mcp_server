package ia.example.mcpserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class  McpServerApplication {

    static void main(String[] args) {
        if ( System.getenv("TOKEN_JIRA") == null) {
            System.err.println("TOKEN_JIRA manquant !");
            System.exit(1);
        } else if(System.getenv("USERNAME_JIRA") == null) {
            System.err.println("USERNAME_JIRA manquant !");
            System.exit(1);
        } else if(System.getenv("TOKEN_GITHUB") == null) {
            System.err.println("TOKEN_GITHUB manquant !");
            System.exit(1);
        }

        SpringApplication.run(McpServerApplication.class, args);
    }
}
