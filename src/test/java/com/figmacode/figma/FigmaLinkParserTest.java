package com.figmacode.figma;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FigmaLinkParserTest {

    @Test
    void parsesDesignLink() {
        FigmaLink link = FigmaLinkParser.parse("https://www.figma.com/design/AbC123xyz/My-File?node-id=12-345&t=abc-1");
        assertThat(link.fileKey()).isEqualTo("AbC123xyz");
        assertThat(link.nodeId()).isEqualTo("12:345");
    }

    @Test
    void parsesLegacyFileLinkWithEncodedColon() {
        FigmaLink link = FigmaLinkParser.parse("https://www.figma.com/file/KEY987/Name?type=design&node-id=1%3A2&mode=dev");
        assertThat(link.fileKey()).isEqualTo("KEY987");
        assertThat(link.nodeId()).isEqualTo("1:2");
    }

    @Test
    void usesBranchKeyForBranchLinks() {
        FigmaLink link = FigmaLinkParser.parse("https://www.figma.com/design/MAIN1/branch/BRANCH2/File?node-id=3-4");
        assertThat(link.fileKey()).isEqualTo("BRANCH2");
        assertThat(link.nodeId()).isEqualTo("3:4");
    }

    @Test
    void keepsInstanceNodeIds() {
        FigmaLink link = FigmaLinkParser.parse("https://figma.com/design/K/F?node-id=I5-6;7-8");
        assertThat(link.nodeId()).isEqualTo("I5:6;7:8");
    }

    @Test
    void rejectsLinksWithoutNode() {
        assertThatThrownBy(() -> FigmaLinkParser.parse("https://www.figma.com/design/K/F"))
                .hasMessageContaining("node-id");
    }

    @Test
    void rejectsOtherHosts() {
        assertThatThrownBy(() -> FigmaLinkParser.parse("https://evil.example.com/design/K/F?node-id=1-2"))
                .hasMessageContaining("figma.com");
        assertThatThrownBy(() -> FigmaLinkParser.parse("https://figma.com.evil.io/design/K/F?node-id=1-2"))
                .hasMessageContaining("figma.com");
    }
}
