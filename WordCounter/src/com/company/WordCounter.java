package com.company;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WordCounter {
    private static final String NON_WORD_REGEX = "\\W+";
    private static final String EXCLUDE_FILE_NAME = "exclude.txt";

    private static final ConcurrentMap<String, Integer> wordsMap = new ConcurrentHashMap<>();
    private static final Set<String> excludeWords = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static Path rootPath;


    public static void process(String [] filePaths) {
        if (filePaths.length == 0) {
            throw new IllegalArgumentException("Must have at least one argument");
        }
        rootPath = getRootPath(filePaths[0]);
        for (String path: filePaths)  {
            if (isExcludeFile(path)) {
                loadExcludeWords(path);
            } else {
                loadWordsMap(path);
            }
        }
        countExcludedWords();
        removeExcludedWords();
        classifyAndWriteWords();
    }

    private static boolean isExcludeFile(String absolutePath) {
        Path path = Paths.get(absolutePath);
        return EXCLUDE_FILE_NAME.equals(path.getFileName().toString());
    }

    private static void loadWordsMap(String path) {
        processLines(path, line -> splitWords(line)
                .forEach(w -> wordsMap.put(w, wordsMap.getOrDefault(w, 0) + 1)));
    }

    private static void loadExcludeWords(String path) {
        processLines(path, line -> excludeWords.addAll(splitWords(line).collect(Collectors.toSet())));
    }

    private static void processLines(String path, Consumer<String> lineProcessor) {
        try (Stream<String> lines = Files.lines(Paths.get(path))) {
            lines.forEach(lineProcessor);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static Stream<String> splitWords(String line) {
        return Arrays.stream(line.toLowerCase().split(NON_WORD_REGEX));
    }

    private static void countExcludedWords() {
        int count = wordsMap.entrySet().stream()
                .filter(entry -> excludeWords.contains(entry.getKey()))
                .mapToInt(Map.Entry::getValue)
                .reduce(0, Integer::sum);

        writeToFile(Paths.get(rootPath.toString(),"/excluded_count.txt"), String.valueOf(count));
    }

    private static void removeExcludedWords() {
        excludeWords.forEach(wordsMap::remove);
    }

    private static Path getRootPath(String absolutePath) {
        //assume that all files are in the same directory
        return Paths.get(absolutePath).getParent();
    }

    private static void classifyAndWriteWords() {
        ConcurrentMap<Character, ConcurrentMap<String, Integer>> classifiedWordsMap = new ConcurrentHashMap<>();
        classifyWordsByInitialLetter(classifiedWordsMap);
        writeClassifiedWords(classifiedWordsMap);
    }

    private static void classifyWordsByInitialLetter(ConcurrentMap<Character, ConcurrentMap<String, Integer>> classifiedWordsMap) {
        wordsMap.forEach((key, value) -> {
            if (key != null && !key.isBlank()){
                char initialLetter = key.charAt(0);
                ConcurrentMap<String, Integer> wordsMap = classifiedWordsMap.getOrDefault(initialLetter, new ConcurrentHashMap<>());
                wordsMap.put(key, value);
                classifiedWordsMap.put(initialLetter, wordsMap);
            }
        });
    }

    private static void writeClassifiedWords(ConcurrentMap<Character, ConcurrentMap<String, Integer>> classifiedWordsMap) {
        classifiedWordsMap.forEach((key, value) -> {
            List<String> contentStrings = new ArrayList<>();
            value.forEach((word, count) -> {
                contentStrings.add(word);
                contentStrings.add(String.valueOf(count));
            });
            String fileContent = String.join(" ", contentStrings);
            writeToFile(Paths.get(rootPath.toString(), String.format("/FILE_%s.txt", key)), fileContent);
        });
    }

    private static void writeToFile(Path path, String content) {
        try {
            Files.write(path, content.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
