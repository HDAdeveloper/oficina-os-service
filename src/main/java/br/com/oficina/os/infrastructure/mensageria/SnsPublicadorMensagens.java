package br.com.oficina.os.infrastructure.mensageria;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.MessageAttributeValue;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

public class SnsPublicadorMensagens implements PublicadorMensagens {

    private final SnsClient sns;
    private final String topicoArn;

    public SnsPublicadorMensagens(SnsClient sns, String topicoArn) {
        this.sns = sns;
        this.topicoArn = topicoArn;
    }

    @Override
    public void publicar(String tipo, String destino, String envelopeJson) {
        sns.publish(PublishRequest.builder().topicArn(topicoArn).message(envelopeJson)
                .messageAttributes(Map.of(
                        "type", atributo(tipo),
                        "target", atributo(destino)))
                .build());
    }

    private static MessageAttributeValue atributo(String valor) {
        return MessageAttributeValue.builder().dataType("String").stringValue(valor).build();
    }
}
