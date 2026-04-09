package com.atendimento.cerebro.infrastructure.adapter.inbound.rest.camel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class WhatsAppWebhookParserTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final WhatsAppWebhookParser parser = new WhatsAppWebhookParser();

    @Test
    void evolution_messagesUpsert_incomingText() throws Exception {
        String json =
                """
                {
                  "event": "messages.upsert",
                  "instance": "cerebro",
                  "data": {
                    "key": {
                      "remoteJid": "557196248348@s.whatsapp.net",
                      "fromMe": false
                    },
                    "pushName": "João Cliente",
                    "message": { "conversation": "Olá" },
                    "messageType": "conversation"
                  }
                }
                """;
        JsonNode root = mapper.readTree(json);
        var in = parser.parse(root);
        assertThat(in).isInstanceOf(WhatsAppWebhookParser.Incoming.TextMessage.class);
        var tm = (WhatsAppWebhookParser.Incoming.TextMessage) in;
        assertThat(tm.fromRaw()).isEqualTo("557196248348");
        assertThat(tm.text()).isEqualTo("Olá");
        assertThat(tm.evolutionLineDigits()).isNull();
        assertThat(tm.providerMessageId()).isNull();
        assertThat(tm.contactDisplayName()).isEqualTo("João Cliente");
        assertThat(tm.contactProfilePicUrl()).isNull();
    }

    @Test
    void evolution_messagesUpsert_readsProfilePicUrl() throws Exception {
        String json =
                """
                {
                  "event": "messages.upsert",
                  "data": {
                    "key": {
                      "remoteJid": "557196248348@s.whatsapp.net",
                      "fromMe": false
                    },
                    "pushName": "João Cliente",
                    "profilePicUrl": "https://cdn.example.com/pic.jpg",
                    "message": { "conversation": "Olá" },
                    "messageType": "conversation"
                  }
                }
                """;
        JsonNode root = mapper.readTree(json);
        var in = parser.parse(root);
        assertThat(in).isInstanceOf(WhatsAppWebhookParser.Incoming.TextMessage.class);
        var tm = (WhatsAppWebhookParser.Incoming.TextMessage) in;
        assertThat(tm.contactProfilePicUrl()).isEqualTo("https://cdn.example.com/pic.jpg");
    }

    @Test
    void evolution_messagesUpsert_includesKeyId() throws Exception {
        String json =
                """
                {
                  "event": "messages.upsert",
                  "data": {
                    "key": {
                      "remoteJid": "557196248348@s.whatsapp.net",
                      "fromMe": false,
                      "id": "3EB0AAAFOCUS"
                    },
                    "message": { "conversation": "Olá" },
                    "messageType": "conversation"
                  }
                }
                """;
        JsonNode root = mapper.readTree(json);
        var in = parser.parse(root);
        assertThat(in).isInstanceOf(WhatsAppWebhookParser.Incoming.TextMessage.class);
        var tm = (WhatsAppWebhookParser.Incoming.TextMessage) in;
        assertThat(tm.providerMessageId()).isEqualTo("3EB0AAAFOCUS");
    }

    @Test
    void evolution_messagesUpsert_lid_usesRemoteJidAlt_andSenderLine() throws Exception {
        String json =
                """
                {
                  "event": "messages.upsert",
                  "sender": "557196248348@s.whatsapp.net",
                  "data": {
                    "key": {
                      "remoteJid": "128776054800618@lid",
                      "remoteJidAlt": "557181757718@s.whatsapp.net",
                      "fromMe": false
                    },
                    "message": { "conversation": "Oi" },
                    "messageType": "conversation"
                  }
                }
                """;
        JsonNode root = mapper.readTree(json);
        var in = parser.parse(root);
        assertThat(in).isInstanceOf(WhatsAppWebhookParser.Incoming.TextMessage.class);
        var tm = (WhatsAppWebhookParser.Incoming.TextMessage) in;
        assertThat(tm.fromRaw()).isEqualTo("557181757718");
        assertThat(tm.text()).isEqualTo("Oi");
        assertThat(tm.evolutionLineDigits()).isEqualTo("557196248348");
    }

    @Test
    void evolution_messagesUpsert_fromMe_ignored() throws Exception {
        String json =
                """
                {
                  "event": "messages.upsert",
                  "data": {
                    "key": { "remoteJid": "5511999999999@s.whatsapp.net", "fromMe": true },
                    "message": { "conversation": "eco" }
                  }
                }
                """;
        assertThat(parser.parse(mapper.readTree(json)))
                .isInstanceOf(WhatsAppWebhookParser.Incoming.Ignored.class);
    }

    @Test
    void evolution_group_ignored() throws Exception {
        String json =
                """
                {
                  "event": "messages.upsert",
                  "data": {
                    "key": { "remoteJid": "120363@g.us", "fromMe": false },
                    "message": { "conversation": "hi" }
                  }
                }
                """;
        assertThat(parser.parse(mapper.readTree(json)))
                .isInstanceOf(WhatsAppWebhookParser.Incoming.Ignored.class);
    }

    @Test
    void simpleJson_stillWorks() throws Exception {
        String json = "{\"from\": \"5571996248348\", \"text\": \"teste\"}";
        var in = parser.parse(mapper.readTree(json));
        assertThat(in).isInstanceOf(WhatsAppWebhookParser.Incoming.TextMessage.class);
        assertThat(((WhatsAppWebhookParser.Incoming.TextMessage) in).providerMessageId()).isNull();
    }

    @Test
    void meta_envelope_includesMessageId() throws Exception {
        String json =
                """
                {
                  "object": "whatsapp_business_account",
                  "entry": [
                    {
                      "changes": [
                        {
                          "value": {
                            "messages": [
                              {
                                "from": "5511999999999",
                                "type": "text",
                                "id": "wamid.meta123",
                                "text": { "body": "Oi" }
                              }
                            ]
                          }
                        }
                      ]
                    }
                  ]
                }
                """;
        var in = parser.parse(mapper.readTree(json));
        assertThat(in).isInstanceOf(WhatsAppWebhookParser.Incoming.TextMessage.class);
        var tm = (WhatsAppWebhookParser.Incoming.TextMessage) in;
        assertThat(tm.providerMessageId()).isEqualTo("wamid.meta123");
        assertThat(tm.text()).isEqualTo("Oi");
    }
}
