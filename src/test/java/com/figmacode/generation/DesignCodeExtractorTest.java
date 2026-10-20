package com.figmacode.generation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class DesignCodeExtractorTest {

    @Test
    void picksCodePartAndKeepsNotes() {
        String code = """
                const imgLogo = "https://www.figma.com/api/mcp/asset/abc-123";
                export default function HeroCard() {
                  return (<div className="p-4"><img src={imgLogo} /></div>);
                }
                """;
        String notes = "SUPER CRITICAL: convert this to your stack.";
        DesignCodeExtractor.Extracted result = DesignCodeExtractor.extract(List.of(notes, code));

        assertThat(result.code()).contains("export default function HeroCard");
        assertThat(result.notes()).contains("SUPER CRITICAL");
        assertThat(result.componentName()).isEqualTo("HeroCard");
        assertThat(result.assetUrls()).containsEntry("imgLogo", "https://www.figma.com/api/mcp/asset/abc-123");
    }

    @Test
    void unwrapsMarkdownFences() {
        String text = "Here is the code:\n```tsx\nexport function Button() { return (<button className=\"x\"/>); }\n```\nDone.";
        DesignCodeExtractor.Extracted result = DesignCodeExtractor.extract(List.of(text));
        assertThat(result.code()).startsWith("export function Button()");
        assertThat(result.componentName()).isEqualTo("Button");
    }

    @Test
    void namesUnnamedAssets() {
        String code = "export default function A(){ return (<img className=\"a\" src=\"http://localhost:3845/assets/x.png\"/>); }";
        assertThat(DesignCodeExtractor.extract(List.of(code)).assetUrls())
                .containsEntry("asset1", "http://localhost:3845/assets/x.png");
    }
}
