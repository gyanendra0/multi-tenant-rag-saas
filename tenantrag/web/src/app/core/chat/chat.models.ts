/** Mirrors backend chat DTOs. */
export interface Citation {
    documentId: string;
    chunkId: string;
    chunkIndex: number;
    title: string;
    score: number;
  }
  
  export interface ChatRequest {
    question: string;
    conversationId?: string | null;
  }
  
  export interface ChatResponse {
    conversationId: string;
    messageId: string;
    answer: string;
    citations: Citation[];
  }
  
  export interface ConversationSummary {
    id: string;
    title: string | null;
    createdAt: string;
    updatedAt: string;
  }
  
  export interface ChatMessage {
    id: string;
    role: 'user' | 'assistant';
    content: string;
    citations: Citation[];
    createdAt: string;
  }
  