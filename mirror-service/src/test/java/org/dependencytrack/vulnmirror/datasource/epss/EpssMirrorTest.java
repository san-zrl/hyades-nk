package org.dependencytrack.vulnmirror.datasource.epss;

import io.github.jeremylong.openvulnerability.client.epss.EpssDataFeed;
import io.github.jeremylong.openvulnerability.client.epss.EpssItem;
import io.quarkus.test.InjectMock;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.Serdes;
import org.dependencytrack.common.KafkaTopic;
import org.dependencytrack.proto.KafkaProtobufSerde;
import org.dependencytrack.proto.notification.v1.Notification;
import org.dependencytrack.vulnmirror.TestConstants;
import org.junit.jupiter.api.Test;
import org.mockito.MockitoAnnotations;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.assertThatNoException;
import static org.dependencytrack.proto.notification.v1.Group.GROUP_DATASOURCE_MIRRORING;
import static org.dependencytrack.proto.notification.v1.Level.LEVEL_ERROR;
import static org.dependencytrack.proto.notification.v1.Level.LEVEL_INFORMATIONAL;
import static org.dependencytrack.proto.notification.v1.Scope.SCOPE_SYSTEM;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
class EpssMirrorTest {

    @Inject
    EpssMirror epssMirror;

    @InjectMock
    EpssClientFactory epssClientFactoryMock;

    @InjectKafkaCompanion
    KafkaCompanion kafkaCompanion;

    @Test
    void testDoMirrorSuccess() {
        MockitoAnnotations.initMocks(this);

        final var epssClientMock = mock(EpssDataFeed.class);
        List<EpssItem> mockEpssList = new ArrayList<>();
        mockEpssList.add(new EpssItem("CVE-123", 3.4, 7.8));
        mockEpssList.add(new EpssItem("CVE-456", 3.5, 7.9));
        when(epssClientMock.download()).thenReturn(mockEpssList);

        when(epssClientFactoryMock.create(any())).thenReturn(epssClientMock);

        epssMirror.doMirror(null);
        final List<ConsumerRecord<String, org.dependencytrack.proto.mirror.v1.EpssItem>> epssItems = kafkaCompanion
                .consume(Serdes.String(), new KafkaProtobufSerde<>(org.dependencytrack.proto.mirror.v1.EpssItem.parser()))
                .withGroupId(TestConstants.CONSUMER_GROUP_ID)
                .withAutoCommit()
                .fromTopics(KafkaTopic.VULNERABILITY_MIRROR_EPSS.getName(), 2, Duration.ofSeconds(5))
                .awaitCompletion()
                .getRecords();
        assertThat(epssItems).satisfiesExactlyInAnyOrder(
                record -> {
                    assertThat(record.key()).isEqualTo("CVE-123");
                    assertThat(record.value().getEpss()).isEqualTo(3.4);
                },
                record -> {
                    assertThat(record.key()).isEqualTo("CVE-456");
                    assertThat(record.value().getEpss()).isEqualTo(3.5);
                }
        );

        final List<ConsumerRecord<String, Notification>> notificationRecords = kafkaCompanion
                .consume(Serdes.String(), new KafkaProtobufSerde<>(Notification.parser()))
                .withGroupId(TestConstants.CONSUMER_GROUP_ID)
                .withAutoCommit()
                .fromTopics(KafkaTopic.NOTIFICATION_DATASOURCE_MIRRORING.getName(), 1, Duration.ofSeconds(5))
                .awaitCompletion()
                .getRecords();
        assertThat(notificationRecords).satisfiesExactly(
                record -> {
                    assertThat(record.key()).isNull();
                    assertThat(record.value().getScope()).isEqualTo(SCOPE_SYSTEM);
                    assertThat(record.value().getGroup()).isEqualTo(GROUP_DATASOURCE_MIRRORING);
                    assertThat(record.value().getLevel()).isEqualTo(LEVEL_INFORMATIONAL);
                    assertThat(record.value().getTitle()).isEqualTo("EPSS Mirroring");
                    assertThat(record.value().getContent()).isEqualTo("Mirroring of the Exploit Prediction Scoring System (EPSS) completed successfully.");
                }
        );
    }

    @Test
    void testDoMirrorEmptyList() {
        MockitoAnnotations.initMocks(this);

        final var epssClientMock = mock(EpssDataFeed.class);
        when(epssClientMock.download()).thenReturn(Collections.EMPTY_LIST);

        when(epssClientFactoryMock.create(any())).thenReturn(epssClientMock);

        epssMirror.doMirror(null);
        final List<ConsumerRecord<String, org.dependencytrack.proto.mirror.v1.EpssItem>> epssItems = kafkaCompanion
                .consume(Serdes.String(), new KafkaProtobufSerde<>(org.dependencytrack.proto.mirror.v1.EpssItem.parser()))
                .withGroupId(TestConstants.CONSUMER_GROUP_ID)
                .withAutoCommit()
                .fromTopics(KafkaTopic.VULNERABILITY_MIRROR_EPSS.getName(), 0, Duration.ofSeconds(5))
                .awaitCompletion()
                .getRecords();

        final List<ConsumerRecord<String, Notification>> notificationRecords = kafkaCompanion
                .consume(Serdes.String(), new KafkaProtobufSerde<>(Notification.parser()))
                .withGroupId(TestConstants.CONSUMER_GROUP_ID)
                .withAutoCommit()
                .fromTopics(KafkaTopic.NOTIFICATION_DATASOURCE_MIRRORING.getName(), 1, Duration.ofSeconds(5))
                .awaitCompletion()
                .getRecords();
        assertThat(notificationRecords).satisfiesExactly(
                record -> {
                    assertThat(record.key()).isNull();
                    assertThat(record.value().getScope()).isEqualTo(SCOPE_SYSTEM);
                    assertThat(record.value().getGroup()).isEqualTo(GROUP_DATASOURCE_MIRRORING);
                    assertThat(record.value().getLevel()).isEqualTo(LEVEL_ERROR);
                    assertThat(record.value().getTitle()).isEqualTo("EPSS Mirroring");
                    assertThat(record.value().getContent()).isEqualTo("An error occurred mirroring the contents of the Exploit Prediction Scoring System (EPSS), cause being: java.lang.IllegalArgumentException: List must contain exactly one EPSS item. Check log for details.");
                }
        );
    }

    @Test
    void testDoMirrorFailure() {
        doThrow(new IllegalStateException()).when(epssClientFactoryMock).create(any());
        assertThatNoException().isThrownBy(() -> epssMirror.doMirror(null).get());

        final List<ConsumerRecord<String, Notification>> notificationRecords = kafkaCompanion
                .consume(Serdes.String(), new KafkaProtobufSerde<>(Notification.parser()))
                .withGroupId(TestConstants.CONSUMER_GROUP_ID)
                .withAutoCommit()
                .fromTopics(KafkaTopic.NOTIFICATION_DATASOURCE_MIRRORING.getName(), 1, Duration.ofSeconds(5))
                .awaitCompletion()
                .getRecords();

        assertThat(notificationRecords).satisfiesExactly(
                record -> {
                    assertThat(record.key()).isNull();
                    assertThat(record.value().getScope()).isEqualTo(SCOPE_SYSTEM);
                    assertThat(record.value().getGroup()).isEqualTo(GROUP_DATASOURCE_MIRRORING);
                    assertThat(record.value().getLevel()).isEqualTo(LEVEL_ERROR);
                    assertThat(record.value().getTitle()).isEqualTo("EPSS Mirroring");
                    assertThat(record.value().getContent()).isEqualTo("An error occurred mirroring the contents of the Exploit Prediction Scoring System (EPSS), cause being: java.lang.IllegalStateException. Check log for details.");
                }
        );
    }
}
