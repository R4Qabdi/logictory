package com.logictory.ui;

import com.logictory.ui.freeplay.GatePainter;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

/** A local campaign catalog preview; level gameplay is not implemented yet. */
public final class LocalLevelSelectPanel extends JPanel {
    private static final Color TILE_BG = new Color(0x2a2c34);
    private static final Color SELECTED_BG = new Color(0x343741);
    private static final Color AMBER = new Color(0xffb454);
    private static final Color GREEN = new Color(0x5fd07a);
    private static final Color CORAL = new Color(0xe88770);

    private static final List<LevelPreview> LEVELS = List.of(
            new LevelPreview("01", "First Signal", "Learn how a signal moves through a circuit.", "Basic", AMBER),
            new LevelPreview("02", "Gate Basics", "Combine simple gates to shape an output.", "Basic", GREEN),
            new LevelPreview("03", "Switchyard", "Route changing inputs through the right path.", "Combination", CORAL),
            new LevelPreview("04", "Tower Grid", "Build a larger network from smaller logic blocks.", "Combination", AMBER),
            new LevelPreview("05", "Final Circuit", "Bring the chapter skills together in one final design.", "Challenges", GREEN)
    );

    private final Runnable onBack;
    private final JPanel chapterList = new JPanel();
    private final JLabel previewChapter = new JLabel();
    private final JLabel previewNumber = new JLabel();
    private final JLabel previewName = new JLabel();
    private final JLabel previewDescription = new JLabel();
    private final JLabel previewStatus = new JLabel("LEVEL PREVIEW ONLY  /  GAMEPLAY NOT AVAILABLE");
    private final List<JButton> levelButtons = new ArrayList<>();
    private LevelPreview selectedLevel;

    public LocalLevelSelectPanel(Runnable onBack) {
        this.onBack = onBack;
        setLayout(new BorderLayout(0, 22));
        setBackground(GatePainter.BG);
        setBorder(new EmptyBorder(22, 30, 24, 30));
        add(buildHeader(), BorderLayout.NORTH);
        add(buildContent(), BorderLayout.CENTER);
        add(buildFooter(), BorderLayout.SOUTH);

        selectLevel(LEVELS.getLast());
    }

    private JPanel buildHeader() {
        JPanel header = new JPanel(new BorderLayout(14, 14));
        header.setOpaque(false);

        JPanel topBar = new JPanel(new BorderLayout());
        topBar.setOpaque(false);
        JButton backButton = new JButton("\u2190  Back");
        backButton.setName("level-select-back");
        backButton.setFocusable(false);
        backButton.addActionListener(event -> onBack.run());
        topBar.add(backButton, BorderLayout.WEST);

        JLabel appName = new JLabel("logictory");
        appName.setName("app-name");
        appName.setForeground(GatePainter.TEXT);
        appName.setFont(appName.getFont().deriveFont(Font.BOLD, 20f));
        topBar.add(appName, BorderLayout.CENTER);

        JLabel campaign = new JLabel("LOCAL CAMPAIGN", SwingConstants.RIGHT);
        campaign.setForeground(AMBER);
        campaign.setFont(campaign.getFont().deriveFont(Font.BOLD, 11f));
        topBar.add(campaign, BorderLayout.EAST);

        JPanel heading = new JPanel(new BorderLayout(4, 4));
        heading.setOpaque(false);
        JLabel title = new JLabel("Choose your next circuit");
        title.setForeground(GatePainter.TEXT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 26f));
        JLabel subtitle = new JLabel("Explore the chapters and preview each level.");
        subtitle.setForeground(GatePainter.TEXT_DIM);
        subtitle.setFont(subtitle.getFont().deriveFont(Font.PLAIN, 12f));
        heading.add(title, BorderLayout.NORTH);
        heading.add(subtitle, BorderLayout.CENTER);

        header.add(topBar, BorderLayout.NORTH);
        header.add(heading, BorderLayout.CENTER);
        return header;
    }

    private JPanel buildContent() {
        JPanel content = new JPanel(new GridBagLayout());
        content.setOpaque(false);

        GridBagConstraints listConstraints = new GridBagConstraints();
        listConstraints.gridx = 0;
        listConstraints.gridy = 0;
        listConstraints.weightx = 2;
        listConstraints.weighty = 1;
        listConstraints.fill = GridBagConstraints.BOTH;
        listConstraints.insets = new Insets(0, 0, 0, 14);
        content.add(buildChapterList(), listConstraints);

        GridBagConstraints previewConstraints = new GridBagConstraints();
        previewConstraints.gridx = 1;
        previewConstraints.gridy = 0;
        previewConstraints.weightx = 1;
        previewConstraints.weighty = 1;
        previewConstraints.fill = GridBagConstraints.BOTH;
        previewConstraints.insets = new Insets(0, 14, 0, 0);
        content.add(buildPreview(), previewConstraints);
        return content;
    }

    private JPanel buildChapterList() {
        chapterList.setLayout(new GridBagLayout());
        chapterList.setOpaque(false);
        chapterList.setName("chapter-list");

        List<String> chapters = LEVELS.stream().map(LevelPreview::chapter).distinct().toList();
        int row = 0;
        for (String chapter : chapters) {
            List<LevelPreview> chapterLevels = LEVELS.stream()
                    .filter(level -> level.chapter().equals(chapter))
                    .toList();
            JLabel heading = new JLabel(chapter.toUpperCase());
            heading.setName("chapter-" + chapter.toLowerCase());
            heading.setForeground(GatePainter.TEXT_DIM);
            heading.setFont(heading.getFont().deriveFont(Font.BOLD, 11f));
            GridBagConstraints headingConstraints = new GridBagConstraints();
            headingConstraints.gridx = 0;
            headingConstraints.gridy = row++;
            headingConstraints.weightx = 1;
            headingConstraints.fill = GridBagConstraints.HORIZONTAL;
            headingConstraints.anchor = GridBagConstraints.WEST;
            headingConstraints.insets = new Insets(3, 0, 7, 0);
            chapterList.add(heading, headingConstraints);

            for (LevelPreview level : chapterLevels) {
                chapterList.add(createLevelButton(level), levelConstraints(row++));
            }
        }

        GridBagConstraints fillerConstraints = new GridBagConstraints();
        fillerConstraints.gridx = 0;
        fillerConstraints.gridy = row;
        fillerConstraints.weighty = 1;
        JPanel filler = new JPanel();
        filler.setOpaque(false);
        chapterList.add(filler, fillerConstraints);
        return chapterList;
    }

    private GridBagConstraints levelConstraints(int row) {
        GridBagConstraints constraints = new GridBagConstraints();
        constraints.gridx = 0;
        constraints.gridy = row;
        constraints.weightx = 1;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        constraints.insets = new Insets(0, 0, 6, 0);
        return constraints;
    }

    private JButton createLevelButton(LevelPreview level) {
        JButton button = new JButton();
        button.setName("level-" + level.number());
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setFocusPainted(false);
        button.setOpaque(true);
        button.setBackground(TILE_BG);
        button.setForeground(GatePainter.TEXT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, level.accent()),
                new EmptyBorder(10, 12, 10, 12)));
        button.setLayout(new BorderLayout(12, 0));

        JLabel number = new JLabel(level.number());
        number.setForeground(level.accent());
        number.setFont(number.getFont().deriveFont(Font.BOLD, 12f));
        JLabel name = new JLabel(level.name());
        name.setForeground(GatePainter.TEXT);
        name.setFont(name.getFont().deriveFont(Font.BOLD, 13f));
        JLabel hint = new JLabel("Preview  \u2192");
        hint.setForeground(GatePainter.TEXT_DIM);
        hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 10f));
        JPanel text = new JPanel(new BorderLayout(0, 3));
        text.setOpaque(false);
        text.add(name, BorderLayout.NORTH);
        text.add(hint, BorderLayout.CENTER);
        button.add(number, BorderLayout.WEST);
        button.add(text, BorderLayout.CENTER);
        button.addActionListener(event -> selectLevel(level));
        levelButtons.add(button);
        return button;
    }

    private JPanel buildPreview() {
        JPanel preview = new JPanel(new BorderLayout(0, 18));
        preview.setName("level-preview");
        preview.setBackground(TILE_BG);
        preview.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 3, 0, 0, AMBER),
                new EmptyBorder(22, 22, 22, 22)));

        JLabel eyebrow = new JLabel("SELECTED LEVEL");
        eyebrow.setForeground(AMBER);
        eyebrow.setFont(eyebrow.getFont().deriveFont(Font.BOLD, 10f));
        preview.add(eyebrow, BorderLayout.NORTH);

        JPanel details = new JPanel(new BorderLayout(0, 12));
        details.setOpaque(false);
        JPanel identity = new JPanel(new BorderLayout(6, 6));
        identity.setOpaque(false);
        previewChapter.setForeground(GatePainter.TEXT_DIM);
        previewChapter.setFont(previewChapter.getFont().deriveFont(Font.BOLD, 11f));
        previewNumber.setForeground(GREEN);
        previewNumber.setFont(previewNumber.getFont().deriveFont(Font.BOLD, 13f));
        previewName.setName("preview-name");
        previewName.setForeground(GatePainter.TEXT);
        previewName.setFont(previewName.getFont().deriveFont(Font.BOLD, 23f));
        identity.add(previewChapter, BorderLayout.NORTH);
        identity.add(previewNumber, BorderLayout.CENTER);
        identity.add(previewName, BorderLayout.SOUTH);
        details.add(identity, BorderLayout.NORTH);

        previewDescription.setName("preview-description");
        previewDescription.setForeground(GatePainter.TEXT_DIM);
        previewDescription.setFont(previewDescription.getFont().deriveFont(Font.PLAIN, 13f));
        previewDescription.setVerticalAlignment(SwingConstants.TOP);
        details.add(previewDescription, BorderLayout.CENTER);
        preview.add(details, BorderLayout.CENTER);

        previewStatus.setForeground(CORAL);
        previewStatus.setFont(previewStatus.getFont().deriveFont(Font.BOLD, 9f));
        preview.add(previewStatus, BorderLayout.SOUTH);
        return preview;
    }

    private JPanel buildFooter() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setOpaque(false);
        JLabel count = new JLabel("05 LEVELS  /  03 CHAPTERS");
        count.setForeground(GatePainter.TEXT_DIM);
        count.setFont(count.getFont().deriveFont(Font.BOLD, 10f));
        footer.add(count, BorderLayout.WEST);

        JLabel note = new JLabel("CAMPAIGN PREVIEW", SwingConstants.RIGHT);
        note.setForeground(GatePainter.TEXT_DIM);
        note.setFont(note.getFont().deriveFont(Font.BOLD, 10f));
        footer.add(note, BorderLayout.EAST);
        return footer;
    }

    private void selectLevel(LevelPreview level) {
        selectedLevel = level;
        previewChapter.setText(level.chapter().toUpperCase());
        previewNumber.setText("LEVEL " + level.number());
        previewName.setText(level.name());
        previewDescription.setText("<html><div style='width: 210px'>" + level.description() + "</div></html>");
        previewDescription.setToolTipText(level.description());
        for (JButton button : levelButtons) {
            boolean selected = button.getName().equals("level-" + level.number());
            button.setBackground(selected ? SELECTED_BG : TILE_BG);
        }
    }

    String selectedLevelName() {
        return selectedLevel.name();
    }

    private record LevelPreview(String number, String name, String description, String chapter, Color accent) {
    }
}
