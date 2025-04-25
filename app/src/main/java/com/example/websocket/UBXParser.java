package com.example.websocket;

import java.io.File;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

public class UBXParser {

    private static final int SYNC_CHAR_1 = 0xB5;
    private static final int SYNC_CHAR_2 = 0x62;

    // We want to extract 15 NAV-PVT messages
    private static final int MESSAGES_TO_EXTRACT = 15;

    public List<NavPvtData> parseFileAndExtractFifteenNAVPVT(String filePath) {
        List<NavPvtData> result = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(new File(filePath))) {
            byte[] buffer = new byte[8];
            long fileLength = new File(filePath).length();

            // Generate 15 evenly spaced offsets within the file
            long[] targetOffsets = new long[MESSAGES_TO_EXTRACT];
            for (int i = 0; i < MESSAGES_TO_EXTRACT; i++) {
                // The last offset we set to (fileLength - 1) to ensure we catch the end
                if (i == MESSAGES_TO_EXTRACT - 1) {
                    targetOffsets[i] = fileLength - 1;
                } else {
                    targetOffsets[i] = (fileLength * i) / (MESSAGES_TO_EXTRACT - 1);
                }
            }

            int messagesFound = 0;
            int targetIndex = 0;
            long currentOffset = 0;

            while (fis.read(buffer, 0, 2) != -1) {
                currentOffset += 2;

                // Check for UBX sync characters 0xB5, 0x62
                if ((buffer[0] & 0xFF) == SYNC_CHAR_1 && (buffer[1] & 0xFF) == SYNC_CHAR_2) {
                    // Read the next 4 bytes (class, ID, length)
                    fis.read(buffer, 0, 4);
                    currentOffset += 4;

                    int messageClass = buffer[0] & 0xFF;
                    int messageID = buffer[1] & 0xFF;
                    int payloadLength = ByteBuffer.wrap(buffer, 2, 2).order(ByteOrder.LITTLE_ENDIAN).getShort();

                    // Read the payload
                    byte[] payload = new byte[payloadLength];
                    fis.read(payload);
                    currentOffset += payloadLength;

                    // Skip the checksum (2 bytes)
                    fis.read(buffer, 0, 2);
                    currentOffset += 2;

                    // We only care about NAV-PVT messages: class=0x01, ID=0x07
                    if (messageClass == 0x01 && messageID == 0x07) {
                        // Check if we've reached or passed the current target offset
                        if (targetIndex < MESSAGES_TO_EXTRACT && currentOffset >= targetOffsets[targetIndex]) {
                            NavPvtData data = parseNAVPVT(payload);
                            result.add(data);
                            messagesFound++;
                            targetIndex++;

                            // Stop once we have 15 messages
                            if (messagesFound == MESSAGES_TO_EXTRACT) {
                                break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return result;
    }

    private NavPvtData parseNAVPVT(byte[] payload) {
        ByteBuffer buffer = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN);

        // Offsets per UBX NAV-PVT specification:
        //   0..3   => iTOW (u4)
        //   4..5   => year (u2)
        int year = buffer.getShort(4);

        //   6      => month (u1)
        int month = buffer.get(6) & 0xFF;

        //   7      => day (u1)
        int day = buffer.get(7) & 0xFF;

        //   8      => hour (u1)
        int hour = buffer.get(8) & 0xFF;

        //   9      => minute (u1)
        int minute = buffer.get(9) & 0xFF;

        //  10      => second (u1)
        int second = buffer.get(10) & 0xFF;

        //  20      => fixType (u1)
        int fixType = buffer.get(20) & 0xFF;

        //  24..27  => longitude (i4), degrees * 1e-7
        double longitude = buffer.getInt(24) / 1e7;

        //  28..31  => latitude (i4), degrees * 1e-7
        double latitude = buffer.getInt(28) / 1e7;

        //  32..35  => height above ellipsoid (mm)
        double heightEllipsoid = buffer.getInt(32) / 1000.0;

        //  36..39  => height above mean sea level (mm)
        double heightMsl = buffer.getInt(36) / 1000.0;

        //  40..43  => horizontal accuracy (mm)
        double horizontalAccuracy = buffer.getInt(40) / 1000.0;

        //  44..47  => vertical accuracy (mm)
        double verticalAccuracy = buffer.getInt(44) / 1000.0;

        String time = String.format(
                "%04d/%02d/%02d %02d:%02d:%02d",
                year, month, day, hour, minute, second
        );
        String fixTypeDescription = getFixTypeDescription(fixType);

        return new NavPvtData(
                time,
                latitude,
                longitude,
                heightEllipsoid,
                heightMsl,
                horizontalAccuracy,
                verticalAccuracy,
                fixTypeDescription
        );
    }

    private String getFixTypeDescription(int fixType) {
        switch (fixType) {
            case 0x00:
                return "No Fix";
            case 0x01:
                return "Dead Reckoning only";
            case 0x02:
                return "2D Fix";
            case 0x03:
                return "3D Fix";
            case 0x04:
                return "GNSS + Dead Reckoning";
            case 0x05:
                return "Time only fix";
            default:
                return "Unknown Fix Type";
        }
    }
}
