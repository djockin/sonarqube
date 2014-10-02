/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.batch.scm;

import org.sonar.api.batch.fs.InputFile;
import org.sonar.api.batch.scm.BlameCommand.BlameResult;
import org.sonar.api.batch.scm.BlameLine;
import org.sonar.api.batch.sensor.SensorContext;
import org.sonar.api.measures.CoreMetrics;
import org.sonar.api.measures.Metric;
import org.sonar.api.measures.PropertiesBuilder;
import org.sonar.api.utils.DateUtils;

import java.text.Normalizer;
import java.util.List;
import java.util.regex.Pattern;

class DefaultBlameResult implements BlameResult {

  private static final Pattern NON_ASCII_CHARS = Pattern.compile("[^\\x00-\\x7F]");
  private static final Pattern ACCENT_CODES = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");

  private final SensorContext context;

  DefaultBlameResult(SensorContext context) {
    this.context = context;
  }

  @Override
  public void add(InputFile file, List<BlameLine> lines) {

    PropertiesBuilder<Integer, String> authors = propertiesBuilder(CoreMetrics.SCM_AUTHORS_BY_LINE);
    PropertiesBuilder<Integer, String> dates = propertiesBuilder(CoreMetrics.SCM_LAST_COMMIT_DATETIMES_BY_LINE);
    PropertiesBuilder<Integer, String> revisions = propertiesBuilder(CoreMetrics.SCM_REVISIONS_BY_LINE);

    int lineNumber = 1;
    for (BlameLine line : lines) {
      authors.add(lineNumber, normalizeString(line.getAuthor()));
      dates.add(lineNumber, DateUtils.formatDateTime(line.getDate()));
      revisions.add(lineNumber, line.getRevision());

      lineNumber++;
      // SONARPLUGINS-3097 For some SCM blame is missing on last empty line
      if (lineNumber > lines.size() && lineNumber == file.lines()) {
        authors.add(lineNumber, normalizeString(line.getAuthor()));
        dates.add(lineNumber, DateUtils.formatDateTime(line.getDate()));
        revisions.add(lineNumber, line.getRevision());
      }
    }

    ScmSensor.saveMeasures(context, file, authors.buildData(), dates.buildData(), revisions.buildData());

  }

  private String normalizeString(String inputString) {
    String lowerCasedString = inputString.toLowerCase();
    String stringWithoutAccents = removeAccents(lowerCasedString);
    return removeNonAsciiCharacters(stringWithoutAccents);
  }

  private String removeAccents(String inputString) {
    String unicodeDecomposedString = Normalizer.normalize(inputString, Normalizer.Form.NFD);
    return ACCENT_CODES.matcher(unicodeDecomposedString).replaceAll("");
  }

  private String removeNonAsciiCharacters(String inputString) {
    return NON_ASCII_CHARS.matcher(inputString).replaceAll("_");
  }

  private static PropertiesBuilder<Integer, String> propertiesBuilder(Metric metric) {
    return new PropertiesBuilder<Integer, String>(metric);
  }
}