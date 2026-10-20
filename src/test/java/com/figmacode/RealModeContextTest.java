package com.figmacode;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.figmacode.auth.BitbucketAuthenticator;
import com.figmacode.auth.RestBitbucketAuthenticator;
import com.figmacode.figma.FigmaMcpGateway;
import com.figmacode.figma.RemoteFigmaMcpGateway;

/** With mock mode off, the real Bitbucket and Figma integrations are wired in. */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:realmode;DB_CLOSE_DELAY=-1")
class RealModeContextTest {

    @Autowired
    BitbucketAuthenticator bitbucket;

    @Autowired
    FigmaMcpGateway figma;

    @Test
    void usesRealIntegrations() {
        assertThat(bitbucket).isInstanceOf(RestBitbucketAuthenticator.class);
        assertThat(figma).isInstanceOf(RemoteFigmaMcpGateway.class);
    }
}
