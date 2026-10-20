package com.figmacode.generation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.stereotype.Component;

/** Packs a generation into a Vite + React + Tailwind project that runs with npm install && npm run dev. */
@Component
public class ProjectZipper {

    public byte[] zip(Generation generation, List<GenerationAsset> assets) {
        String component = safeComponentName(generation.getComponentName());
        String folder = kebab(component) + "/";
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            text(zip, folder + "package.json", packageJson(kebab(component)));
            text(zip, folder + "vite.config.ts", VITE_CONFIG);
            text(zip, folder + "tsconfig.json", TSCONFIG);
            text(zip, folder + "index.html", indexHtml(component));
            text(zip, folder + "src/main.tsx", mainTsx(component));
            text(zip, folder + "src/index.css", "@import \"tailwindcss\";\n");
            text(zip, folder + "src/" + component + ".tsx", generation.getCode());
            text(zip, folder + "README.md", readme(generation, component));
            if (generation.getNotes() != null && !generation.getNotes().isBlank()) {
                text(zip, folder + "FIGMA_NOTES.md", "# Notes from Figma\n\n" + generation.getNotes() + "\n");
            }
            for (GenerationAsset asset : assets) {
                binary(zip, folder + "public/assets/" + asset.getFileName(), asset.getData());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static String safeComponentName(String name) {
        String cleaned = name == null ? "" : name.replaceAll("[^A-Za-z0-9_]", "");
        if (cleaned.isEmpty() || !Character.isLetter(cleaned.charAt(0))) {
            cleaned = "FigmaDesign" + cleaned;
        }
        return Character.toUpperCase(cleaned.charAt(0)) + cleaned.substring(1);
    }

    public static String kebab(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase();
    }

    private static void text(ZipOutputStream zip, String path, String content) throws IOException {
        binary(zip, path, content.getBytes(StandardCharsets.UTF_8));
    }

    private static void binary(ZipOutputStream zip, String path, byte[] data) throws IOException {
        zip.putNextEntry(new ZipEntry(path));
        zip.write(data);
        zip.closeEntry();
    }

    private static String packageJson(String name) {
        return """
                {
                  "name": "%s",
                  "private": true,
                  "version": "0.1.0",
                  "type": "module",
                  "scripts": {
                    "dev": "vite",
                    "build": "vite build",
                    "preview": "vite preview"
                  },
                  "dependencies": {
                    "react": "^18.3.1",
                    "react-dom": "^18.3.1"
                  },
                  "devDependencies": {
                    "@tailwindcss/vite": "^4.1.18",
                    "@types/react": "^18.3.12",
                    "@types/react-dom": "^18.3.1",
                    "@vitejs/plugin-react": "^4.3.4",
                    "tailwindcss": "^4.1.18",
                    "typescript": "^5.6.3",
                    "vite": "^6.0.0"
                  }
                }
                """.formatted(name);
    }

    private static final String VITE_CONFIG = """
            import { defineConfig } from "vite";
            import react from "@vitejs/plugin-react";
            import tailwindcss from "@tailwindcss/vite";

            export default defineConfig({
              plugins: [react(), tailwindcss()],
            });
            """;

    private static final String TSCONFIG = """
            {
              "compilerOptions": {
                "target": "ES2020",
                "lib": ["ES2020", "DOM", "DOM.Iterable"],
                "module": "ESNext",
                "moduleResolution": "bundler",
                "jsx": "react-jsx",
                "strict": false,
                "skipLibCheck": true,
                "noEmit": true
              },
              "include": ["src"]
            }
            """;

    private static String indexHtml(String component) {
        return """
                <!doctype html>
                <html lang="en">
                  <head>
                    <meta charset="UTF-8" />
                    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                    <title>%s</title>
                  </head>
                  <body>
                    <div id="root"></div>
                    <script type="module" src="/src/main.tsx"></script>
                  </body>
                </html>
                """.formatted(component);
    }

    private static String mainTsx(String component) {
        return """
                import React from "react";
                import { createRoot } from "react-dom/client";
                import * as Generated from "./%1$s";
                import "./index.css";

                // Figma's code usually has a default export; fall back to the first exported component.
                const Component: React.ComponentType =
                  (Generated as any).default ??
                  (Object.values(Generated).find((value) => typeof value === "function") as React.ComponentType);

                createRoot(document.getElementById("root")!).render(
                  <React.StrictMode>
                    <Component />
                  </React.StrictMode>
                );
                """.formatted(component);
    }

    private static String readme(Generation generation, String component) {
        return """
                # %1$s

                Generated from Figma by Figma Code Studio.

                - Figma link: %2$s
                - File key: `%3$s`, node: `%4$s`

                ## Run it

                ```
                npm install
                npm run dev
                ```

                The component lives in `src/%1$s.tsx`. Images are in `public/assets` and are referenced as `/assets/...`.
                """.formatted(component, generation.getFigmaUrl(), generation.getFileKey(), generation.getNodeId());
    }
}
