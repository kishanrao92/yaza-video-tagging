/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.video;

import com.google.api.gax.longrunning.OperationFuture;
import com.google.cloud.videointelligence.v1.AnnotateVideoProgress;
import com.google.cloud.videointelligence.v1.AnnotateVideoRequest;
import com.google.cloud.videointelligence.v1.AnnotateVideoResponse;
import com.google.cloud.videointelligence.v1.Entity;
import com.google.cloud.videointelligence.v1.ExplicitContentFrame;
import com.google.cloud.videointelligence.v1.Feature;
import com.google.cloud.videointelligence.v1.LabelAnnotation;
import com.google.cloud.videointelligence.v1.LabelSegment;
import com.google.cloud.videointelligence.v1.SpeechRecognitionAlternative;
import com.google.cloud.videointelligence.v1.SpeechTranscription;
import com.google.cloud.videointelligence.v1.SpeechTranscriptionConfig;
import com.google.cloud.videointelligence.v1.VideoAnnotationResults;
import com.google.cloud.videointelligence.v1.VideoContext;
import com.google.cloud.videointelligence.v1.VideoIntelligenceServiceClient;
import com.google.cloud.videointelligence.v1.VideoSegment;
import com.google.cloud.videointelligence.v1.WordInfo;
import com.google.protobuf.ByteString;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toMap;


public class Detect {
    /**
     * Detects labels, shots, and explicit content in a video using the Video Intelligence API
     *
     * @param args specifies features to detect and the path to the video on Google Cloud Storage.
     */
    public static void main(String[] args) {
        try {
            argsHelper(args);
        } catch (Exception e) {
            System.out.println("Exception while running:\n" + e.getMessage() + "\n");
            e.printStackTrace(System.out);
        }
    }

    /**
     * Helper that handles the input passed to the program.
     *
     * @param args specifies features to detect and the path to the video on Google Cloud Storage.
     * @throws IOException on Input/Output errors.
     */
    public static void argsHelper(String[] args) throws Exception {
        if(args.length < 1) {
            System.out.println("Usage:");
            System.out.printf(
                    "\tjava %s \"<command>\" \"<path-to-video>\"\n"
                            + "Commands:\n"
                            + "\tlabels | shots\n"
                            + "Path:\n\tA URI for a Cloud Storage resource (gs://...)\n"
                            + "Examples: ",
                    Detect.class.getCanonicalName());
            return;
        }
        String command = args[0];
        String path = args.length > 1 ? args[1] : "";

        if(command.equals("labels-file")) {
            analyzeLabelsFile(path);
        }
    }


    /**
     * Performs label analysis on the video at the provided file path.
     *
     * @param dirPath the path to the video directory to analyze.
     */
    public static void analyzeLabelsFile(String dirPath) throws Exception {

        /*
        Pass in a directory with videos that need to be analyzed. Each video will result in a new API call to the Video
        Intelligence API. We are only doing shot detection here because I found that to be the most accurate for our use case.
        The shot names are stored as keys in a HashMap with the confidence as the value. This map is then sorted on confidence value using a
        comparator and the result is written to an output txt file
         */
        File videoDirectory = new File(dirPath);
        BufferedWriter writer = new BufferedWriter(new FileWriter("Output.txt", true));
        Map<String, Float> confidenceSortedTags = null;
        File[] videoArray = videoDirectory.listFiles();
        for (File f : videoArray) {
            System.out.println("File name is " + f.getName());


            Map<String, Float> videoTags = new HashMap<>();
            // [START video_analyze_labels]
            // Instantiate a com.google.cloud.videointelligence.v1.VideoIntelligenceServiceClient
            try (VideoIntelligenceServiceClient client = VideoIntelligenceServiceClient.create()) {
                // Read file and encode into Base64
                //Path path = Paths.get();
                byte[] data = Files.readAllBytes(f.toPath());

                AnnotateVideoRequest request = AnnotateVideoRequest.newBuilder()
                        .setInputContent(ByteString.copyFrom(data))
                        .addFeatures(Feature.LABEL_DETECTION)
                        .build();
                // Create an operation that will contain the response when the operation completes.
                OperationFuture<AnnotateVideoResponse, AnnotateVideoProgress> response =
                        client.annotateVideoAsync(request);


                System.out.println("Waiting for operation to complete...");
                for (VideoAnnotationResults results : response.get().getAnnotationResultsList()) {
                    // process shot label annotations
                    for (LabelAnnotation labelAnnotation : results.getShotLabelAnnotationsList()) {

                        // segments
                        for (LabelSegment segment : labelAnnotation.getSegmentsList()) {
                            videoTags.put(labelAnnotation.getEntity().getDescription(), segment.getConfidence());
                        }
                    }

                    confidenceSortedTags = videoTags
                            .entrySet()
                            .stream()
                            .sorted(Collections.reverseOrder(Map.Entry.comparingByValue()))
                            .collect(
                                    toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
                                            LinkedHashMap::new));
                }
            }
            writer.append(f.getName());
            writer.append(" ");
            writer.append(confidenceSortedTags.toString());
            writer.newLine();
            System.out.println("Operation completed for file --> "+f.getName());

        }
        writer.close();
        // [END video_analyze_labels]
    }

}