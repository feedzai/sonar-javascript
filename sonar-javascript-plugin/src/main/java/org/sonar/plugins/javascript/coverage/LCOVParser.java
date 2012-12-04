/*
 * Sonar JavaScript Plugin
 * Copyright (C) 2011 Eriks Nukis and SonarSource
 * dev@sonar.codehaus.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonar.plugins.javascript.coverage;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.sonar.api.measures.CoverageMeasuresBuilder;
import org.sonar.api.measures.Measure;
import org.sonar.api.utils.KeyValueFormat;

/**
 * Parses JsTestDriver file coverage report files (generated by
 * http://code.google.com/p/js-test-driver/source/browse/trunk/JsTestDriver/src/com/google/jstestdriver/coverage/LcovWriter.java
 *
 * @author Eriks.Nukis
 */
public final class LCOVParser {

    private static final Logger LOG = LoggerFactory.getLogger(LCOVParser.class);

    public Map<String, CoverageMeasuresBuilder> parseFile(File file) {
        return parseFile(file, null);
    }

    public Map<String, CoverageMeasuresBuilder> parseFile(File file, File basePath) {
        List<String> lines = new LinkedList<String>();
        try {
            lines = FileUtils.readLines(file);
        } catch (IOException e) {
            LOG.debug("Cound not read content from file: {}", file.getAbsolutePath(), e);
        }

        Map<String, CoverageMeasuresBuilder> builderByFilename = Maps.newHashMap();

        File f = null;
        CoverageMeasuresBuilder builder = CoverageMeasuresBuilder.create();
        Map<Integer,Map.Entry<Integer,Integer>> conditions = null;
        for (String line : lines) {

            if (line.startsWith("SF:")) {
                String filePath = line.substring(line.indexOf("SF:") + 3);
                if (basePath != null && filePath.startsWith(".")) {
                    f = new File(basePath.getAbsolutePath(), filePath);
                } else {
                    f = new File(filePath);
                }

                builder = CoverageMeasuresBuilder.create();
                conditions = new HashMap<Integer, Map.Entry<Integer, Integer>>();
            } else if (line.startsWith("DA:")) {
                //  DA:<line number>,<execution count>[,<checksum>]
                String execution = line.substring(3);
                String[] values = execution.split(",");
                builder.setHits(Integer.valueOf(values[0]), Integer.valueOf(values[1]));
            } else if (line.startsWith("BRDA:")) {
                // BRDA:<line number>,<block number>,<branch number>,<taken>
                String execution = line.substring(5);
                String[] values = execution.split(",");
                Integer lineNumber = Integer.valueOf(values[0]);
                Integer branches = 1;
                Integer coveredBRanches = Integer.valueOf(values[3]) > 0 ? 1 : 0;
                if (conditions.get(lineNumber) != null) {
                    branches += conditions.get(lineNumber).getKey();
                    coveredBRanches += conditions.get(lineNumber).getValue();
                }
                conditions.put(lineNumber,new AbstractMap.SimpleEntry<Integer, Integer>(branches,coveredBRanches));

            } else if (line.contains("end_of_record")) {
                // set conditions.
                for (Map.Entry<Integer, Map.Entry<Integer, Integer>> condLine : conditions.entrySet()) {
                    builder.setConditions(condLine.getKey(), condLine.getValue().getKey(), condLine.getValue().getValue());
                }
                try {
                    builderByFilename.put(f.getCanonicalPath(), builder);
                } catch (IOException e) {
                    LOG.error("Error resolving fileName: {}", f.getAbsolutePath());
                }
            }
        }

        return builderByFilename;
    }
}
