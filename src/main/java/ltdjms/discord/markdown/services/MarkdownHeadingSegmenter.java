package ltdjms.discord.markdown.services;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 以標題行為邊界的串流分段器，保留標題層級脈絡。 */
public final class MarkdownHeadingSegmenter {

  private static final Pattern HEADING_LINE = Pattern.compile("^#{1,6}\\s+.+");

  private static final class Heading {
    private final int level;
    private final String line;

    private Heading(int level, String line) {
      this.level = level;
      this.line = line;
    }
  }

  private final StringBuilder buffer = new StringBuilder();
  private final StringBuilder currentSegment = new StringBuilder();
  private final List<Heading> headingStack = new ArrayList<>();
  private boolean inCodeBlock = false;

  public List<String> processChunk(String chunk) {
    if (chunk != null && !chunk.isEmpty()) {
      buffer.append(chunk);
    }

    List<String> segments = new ArrayList<>();
    int newlineIndex;
    while ((newlineIndex = buffer.indexOf("\n")) >= 0) {
      String line = buffer.substring(0, newlineIndex);
      buffer.delete(0, newlineIndex + 1);
      handleLine(line, true, segments);
    }
    return segments;
  }

  public String drain() {
    if (buffer.length() > 0) {
      String line = buffer.toString();
      buffer.setLength(0);
      handleLine(line, false, new ArrayList<>());
    }
    String result = currentSegment.toString();
    currentSegment.setLength(0);
    headingStack.clear();
    inCodeBlock = false;
    return result;
  }

  private void handleLine(String line, boolean appendNewline, List<String> segments) {
    String trimmed = line.trim();
    boolean isFence = trimmed.startsWith("```");
    if (isFence) {
      inCodeBlock = !inCodeBlock;
    }

    if (!inCodeBlock && isHeadingLine(trimmed)) {
      flushSegment(segments);
      updateHeadingStack(trimmed, line);
      rebuildSegmentPrefix();
      if (appendNewline) {
        currentSegment.append("\n");
      }
      return;
    }

    currentSegment.append(line);
    if (appendNewline) {
      currentSegment.append("\n");
    }
  }

  private boolean isHeadingLine(String trimmed) {
    return HEADING_LINE.matcher(trimmed).matches();
  }

  private void flushSegment(List<String> segments) {
    String segment = currentSegment.toString().strip();
    if (!segment.isEmpty()) {
      segments.add(segment);
    }
    currentSegment.setLength(0);
  }

  private void updateHeadingStack(String trimmedLine, String originalLine) {
    int level = 0;
    while (level < trimmedLine.length() && trimmedLine.charAt(level) == '#') {
      level++;
    }

    while (!headingStack.isEmpty() && headingStack.get(headingStack.size() - 1).level >= level) {
      headingStack.remove(headingStack.size() - 1);
    }
    headingStack.add(new Heading(level, originalLine));
  }

  private void rebuildSegmentPrefix() {
    currentSegment.setLength(0);
    for (int i = 0; i < headingStack.size(); i++) {
      currentSegment.append(headingStack.get(i).line);
      if (i < headingStack.size() - 1) {
        currentSegment.append("\n");
      }
    }
  }
}
