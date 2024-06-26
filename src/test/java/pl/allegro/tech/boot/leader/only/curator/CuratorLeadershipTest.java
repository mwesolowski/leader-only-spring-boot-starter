package pl.allegro.tech.boot.leader.only.curator;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import pl.allegro.tech.boot.leader.only.fixtures.SampleApplication;
import pl.allegro.tech.boot.leader.only.fixtures.SampleLeaderOnlyExecutor;

import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.function.Predicate.isEqual;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@SpringBootTest(classes = SampleApplication.class)
class CuratorLeadershipTest {

    private static final int PORT = 2181;

    @Container
    public static final GenericContainer<?> zookeeper = new GenericContainer<>(DockerImageName.parse("zookeeper:3.6.2"))
            .withExposedPorts(PORT);

    @DynamicPropertySource
    static void zookeeperProperties(DynamicPropertyRegistry registry) {
        registry.add("curator-leadership.connection-string", () ->
                zookeeper.getHost() + ":" + zookeeper.getMappedPort(PORT));
        registry.add("curator-leadership.namespace", () -> "test/path");
    }

    @Autowired
    SampleLeaderOnlyExecutor underTest;

    @Test
    void shouldRespondOnlyOnLeader() {
        assertTrue(zookeeper.isRunning());

        await().atMost(5, SECONDS).until(() -> underTest.calculateWhatIsTwoPlusTwo(), isEqual(4));

        assertEquals(1, underTest.getLeadershipAcquisitionCounter());
    }
}
