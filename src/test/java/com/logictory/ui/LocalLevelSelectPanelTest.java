package com.logictory.ui;

import org.junit.jupiter.api.Test;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalLevelSelectPanelTest {

    @Test
        void showsChaptersAndSelectsFinalLevelByDefault() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LocalLevelSelectPanel panel = new LocalLevelSelectPanel(() -> { });

            List<JButton> tiles = buttons(panel).stream()
                .filter(button -> button.getName() != null && button.getName().matches("level-\\d{2}"))
                    .toList();
            assertEquals(5, tiles.size());
            assertEquals("level-05", tiles.getLast().getName());
            assertEquals("Final Circuit", panel.selectedLevelName());

            List<String> chapterNames = labels(panel).stream()
                .map(JLabel::getName)
                .filter(name -> name != null && name.startsWith("chapter-"))
                .toList();
            assertEquals(List.of("chapter-basic", "chapter-combination", "chapter-challenges"), chapterNames);

            JLabel previewName = labels(panel).stream()
                .filter(label -> "preview-name".equals(label.getName()))
                    .findFirst()
                    .orElseThrow();
            assertEquals("Final Circuit", previewName.getText());

            JLabel appName = labels(panel).stream()
                .filter(label -> "app-name".equals(label.getName()))
                .findFirst()
                .orElseThrow();
            assertEquals("logictory", appName.getText());
        });
    }

        @Test
        void selectingLevelUpdatesRightHandPreview() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            LocalLevelSelectPanel panel = new LocalLevelSelectPanel(() -> { });
            JButton firstLevel = buttons(panel).stream()
                .filter(button -> "level-01".equals(button.getName()))
                .findFirst()
                .orElseThrow();

            firstLevel.doClick();

            assertEquals("First Signal", panel.selectedLevelName());
            JLabel previewName = labels(panel).stream()
                .filter(label -> "preview-name".equals(label.getName()))
                .findFirst()
                .orElseThrow();
            assertEquals("First Signal", previewName.getText());
        });
        }

    @Test
    void backButtonInvokesNavigationCallback() throws Exception {
        AtomicBoolean returnedToMenu = new AtomicBoolean();
        SwingUtilities.invokeAndWait(() -> {
            LocalLevelSelectPanel panel = new LocalLevelSelectPanel(() -> returnedToMenu.set(true));
            JButton backButton = buttons(panel).stream()
                    .filter(button -> "level-select-back".equals(button.getName()))
                    .findFirst()
                    .orElseThrow();

            backButton.doClick();
        });

        assertTrue(returnedToMenu.get());
    }

    private static List<JButton> buttons(Container container) {
        List<JButton> found = new ArrayList<>();
        collect(container, JButton.class, found);
        return found;
    }

    private static List<JLabel> labels(Container container) {
        List<JLabel> found = new ArrayList<>();
        collect(container, JLabel.class, found);
        return found;
    }

    private static <T extends Component> void collect(Container container, Class<T> type, List<T> found) {
        for (Component component : container.getComponents()) {
            if (type.isInstance(component)) {
                found.add(type.cast(component));
            }
            if (component instanceof Container child) {
                collect(child, type, found);
            }
        }
    }
}
