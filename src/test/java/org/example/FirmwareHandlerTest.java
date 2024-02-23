package org.example;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class FirmwareHandlerTest {

    static List<Byte> byteArrayToList(byte[] arr) {
        List<Byte> result = new ArrayList<>();

        for (byte b : arr)
            result.add(b);

        return result;
    }

    static List<Byte> getListOfBytes(List<Integer> intArr) {
        List<Byte> result = new ArrayList<>();

        for (Integer b : intArr)
            result.add((byte)(b & 0xff));

        return result;
    }

    static void addSplitCommands(
            int startAddress,
            List<FirmwareHandler.FirmwareCommand> commands,
            List<Integer> intData
    ) {
        List<Byte> bytes = getListOfBytes(intData);

        for (int i = 0; i < bytes.size(); i += 2) {
            commands.add(
                    new FirmwareHandler.FirmwareCommand(
                            FirmwareHandler.getAddressFromInt(startAddress + i),
                            3,
                            bytes.subList(i, i + 2)
                    )
            );
        }
    }

    @Test
    void readBytesFromStream() {
        InputStream input = new ByteArrayInputStream("10053C00B3FB".getBytes());
        ArrayList<Byte> expected = new ArrayList<>();
        expected.add((byte) 0x10);
        expected.add((byte) 0x05);
        expected.add((byte) 0x3c);
        expected.add((byte) 0x00);
        expected.add((byte) 0xb3);
        expected.add((byte) 0xfb);

        try {
            ArrayList<Byte> result = FirmwareHandler.readBytesFromStream(input, 6);

            assertArrayEquals(result.toArray(), expected.toArray());
        } catch (IOException e) {
            fail("IOException");
        }
    }

    @Test
    void getLowPartOfAddress() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(0x08000042);

        int result = FirmwareHandler.getLowPartOfAddress(
                byteArrayToList(buffer.array())
        );

        assertEquals(0x4200, result);
    }

    @Test
    void getAddressFromInt() {
        ByteBuffer buffer = ByteBuffer.allocate(4);
        buffer.order(ByteOrder.BIG_ENDIAN);
        buffer.putInt(0x08ff0a42);

        List<Byte> result = FirmwareHandler.getAddressFromInt(
                0x08ff0a42
        );

        assertEquals(byteArrayToList(buffer.array()), result);
    }

    @Test
    void getMicroprogramPart() {
        List<FirmwareHandler.FirmwareCommand> expected = new ArrayList<>();
        addSplitCommands(0x045a0000, expected, List.of(
                0x00, 0x50, 0x00, 0x20, 0xF5, 0x03, 0x00, 0x08, 0x00,
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
        ));
        addSplitCommands(0x045a2A4C, expected, List.of(
                0x03, 0x93, 0x04, 0x93, 0x05, 0x93, 0x02, 0x23,
                0x06, 0x93, 0x01, 0x23, 0x0A, 0x93, 0x10, 0x23
        ));
        addSplitCommands(0x045a2900, expected, List.of(
                0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x5F, 0xF8, 0x08, 0xF1
        ));
        addSplitCommands(0x045a38AC, expected, List.of(0x9E, 0x46, 0x70, 0x47));
        addSplitCommands(0x045a38B0, expected, List.of(0xAA, 0x55));
        String inputString = ":02000004045AFF\r\n" +
                ":1000000000500020F5030008000000000000000080\r\n" +
                ":102A4C000393049305930223069301230A93102303\r\n" +
                ":0C29000000000000000000005FF808F17B\r\n" +
                ":0438AC009E4670477D\r\n" +
                ":0238B000AA55FF\r\n" +
                ":00000001FF";
        InputStream input = new ByteArrayInputStream(inputString.getBytes());

        try {
            List<FirmwareHandler.FirmwareCommand> result =
                    FirmwareHandler.getMicroprogramPart(input);

            for (int i  = 0; i < result.size(); i++) {
                assertArrayEquals(
                        expected.get(i).getAddress(), result.get(i).getAddress()
                );
                assertArrayEquals(
                        expected.get(i).command_type, result.get(i).command_type
                );
                assertArrayEquals(
                        expected.get(i).getData(), result.get(i).getData()
                );
            }
        } catch (IOException e) {
            fail("IOException");
        }
    }

    @Test
    void getFirstAndLastAddresses() {
        // 0x045A xxxx
        int[] expected = { 0x045a0000, 0x045a38B0 };
        String inputString = ":02000004045AFF\r\n" +
                ":1000000000500020F5030008000000000000000080\n\n" +
                ":102A4C000393049305930223069301230A93102303\n\n" +
                ":0C29000000000000000000005FF808F17B\n\n" +
                ":0438AC009E4670477D\n\n" +
                ":0238B000AA55FF\n\n" +
                ":00000001FF";
        InputStream input = new ByteArrayInputStream(inputString.getBytes());

        try {
            int[] result = FirmwareHandler.getFirstAndLastAddresses(input);

            assertArrayEquals(result, expected);
        } catch (IOException e) {
            fail("IOException");
        }
    }

    @Test
    void getEraseCommand() {
        int[] addresses = { 0x045a0000, 0x045a38B0 };

        try {
            FirmwareHandler.FirmwareCommand result = FirmwareHandler.getEraseCommand(addresses);

            assertArrayEquals(result.command_type, new byte[] { 4 + '0' });
            assertEquals(
                    byteArrayToList(result.getAddress()),
                    FirmwareHandler.getAddressFromInt(addresses[0])
            );
            assertEquals(
                    byteArrayToList(result.getData()),
                    new ArrayList<>(List.of((byte)15))
            );
        } catch (IOException e) {
            fail("IOException");
        }
    }
}