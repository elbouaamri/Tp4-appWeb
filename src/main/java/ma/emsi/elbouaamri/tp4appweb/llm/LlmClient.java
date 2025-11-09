package ma.emsi.elbouaamri.tp4appweb.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class LlmClient {

    private String systemRole;
    private ChatMemory chatMemory;
    private Assistant assistant;

    @Inject
    private RagRoutageService ragRoutageService;

    @Inject
    private PasDeRagService pasDeRagService;

    private ChatModel model;

    @PostConstruct
    public void init() {
        String apiKey = System.getenv("GEMINI_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            apiKey = System.getenv("GOOGLE_AI_API_KEY");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "Clé API manquante. Définissez GEMINI_KEY ou GOOGLE_AI_API_KEY dans vos variables d'environnement."
            );
        }

        this.model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-2.5-flash")
                .temperature(0.3)
                .logRequestsAndResponses(true)
                .build();

        this.chatMemory = MessageWindowChatMemory.withMaxMessages(10);
        System.out.println("✅ LlmClient initialisé avec Gemini prêt !");
    }

    public String ask(String prompt) {
        // Le LLM du PasDeRagService décide si on active le RAG
        RetrievalAugmentor augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(pasDeRagService) // 🔹 remplace les regex par ton QueryRouter LLM
                .build();

        assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(chatMemory)
                .retrievalAugmentor(augmentor)
                .build();

        return assistant.chat(prompt);
    }

    public void setSystemRole(String role) {
        this.systemRole = role;
        chatMemory.clear();
        if (role != null && !role.isBlank()) {
            chatMemory.add(SystemMessage.from(role));
        }
    }

    public String getSystemRole() {
        return systemRole;
    }

    /** Interface AI utilisée par LangChain4j */
    public interface Assistant {
        @dev.langchain4j.service.SystemMessage("""
            Tu disposes de deux sources de connaissance :
            1️⃣ rag.pdf (IA, RAG, fine-tuning)
            2️⃣ emsi.pdf (management, organisation EMSI)
            Utilise la source la plus pertinente selon la question.
            Si la question n’a aucun rapport avec ces thèmes, réponds normalement sans y faire référence.
        """)
        String chat(String userMessage);
    }
}
