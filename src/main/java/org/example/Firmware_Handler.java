package org.example;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class Firmware_Handler {
    private static final ArrayList<Firmware_Command> microprogram =
            new ArrayList<>();
    private static int offset_address;
    private static final ByteBuffer byteBuffer = ByteBuffer.allocate(4);
    public static final byte[] endOfWrite = { 0x33, (byte)0xcc, 0x0, 0x0 };

    private static ArrayList<Byte> readBytesFromStream(
            InputStream in,
            int bytes_num
    ) throws IOException {
        ArrayList<Byte> result = new ArrayList<>();
        String string_byte;

        for (int i = 0; i < bytes_num; i++) {
            string_byte = String.valueOf((char)in.read()) + (char)in.read();
            result.add((byte)Integer.parseInt(string_byte, 16));
        }

        return result;
    }

    private static int getLowPartOfAddressBigEndian(List<Byte>bytes) {
        return ((bytes.get(0) & 0xff) << 8) | (bytes.get(1) & 0xff);
    }

    private static int getLowPartOfAddressLittleEndian(List<Byte>bytes) {
        return ((bytes.get(1) & 0xff) << 8) | (bytes.get(0) & 0xff);
    }

    private static List<Byte> getAddressFromInt(int num) {
        List<Byte> result = new ArrayList<>();

        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        byteBuffer.clear();
        byteBuffer.putInt(num);

        byte[] byteArray = byteBuffer.array();
        for (byte b : byteArray)
            result.add(b);

        return result;
    }

    public static List<Firmware_Command> getMicroprogram(
            InputStream in
    ) throws IOException {
        List<Byte> input;
        microprogram.clear();

        while (true) {
            // skip ':' - start of data line in IntelHEX
            long skipped = in.skip(1);
            if (skipped == 0)
                throw new IOException("Skip error");

            input = readBytesFromStream(in, 4);
            Byte type = input.get(3);
            if (type == 0x01 || type == 0x05) // end of file or start address
                break;
            if (type == 0x04) { // extended linear address
                offset_address = getLowPartOfAddressBigEndian(
                        readBytesFromStream(in, input.get(0))
                ) << 16;
                // skip control sum + '\n'
                skipped = in.skip(4);
                if (skipped == 0)
                    throw new IOException("Skip error");
                continue;
            }

            int address = getLowPartOfAddressBigEndian(input.subList(1, 4)) +
                    offset_address;

            // We split the data into 2 bytes
            List<Byte> data = readBytesFromStream(in, input.get(0));
            for (int i = 0; i < input.get(0) / 2; i++) {
                int addressOffset = 2 * i;
                Firmware_Command command = new Firmware_Command(
                    getAddressFromInt(address + addressOffset),
                    3
                );
                command.data = data.subList(addressOffset, addressOffset + 2);
                microprogram.add(command);
            }

            skipped = in.skip(4);
            if (skipped == 0)
                throw new IOException("Skip error");
        }

        return microprogram;
    }

    public static Firmware_Command getEraseCommand() {
        Firmware_Command command = new Firmware_Command(
                microprogram.get(0).address,
                4
        );

        List<Byte> lastAddress = microprogram.get(microprogram.size() - 1).address;
        List<Byte> firstAddress = microprogram.get(0).address;
//        int lowPartOfHighAddress = ((highAddress.get(1) & 0xff) << 8) |
//                (highAddress.get(0) & 0xff);
//        int lowPartOfLastAddress = ((lastAddress.get(1) & 0xff) << 8) |
//                (lastAddress.get(0) & 0xff);
        int lowPartOfFirstAddress = getLowPartOfAddressLittleEndian(firstAddress);
        int lowPartOfLastAddress = getLowPartOfAddressLittleEndian(lastAddress);
        int microprogramSize = lowPartOfLastAddress - lowPartOfFirstAddress;

        byte numOfPages = (byte) Math.ceil((double)microprogramSize / 1024);
        command.data = new ArrayList<>(List.of(numOfPages));

        return command;
    }

    public static class Firmware_Command {
        private final List<Byte> address;
        private List<Byte> data;
        public byte[] command_type = new byte[1];

        Firmware_Command(List<Byte> address, int command_type) {
            this.address = address;
            this.command_type[0] = (byte)(command_type + '0');
        }

        static byte[] getByteArrayFromList(List<Byte> list) {
            byte[] result = new byte[list.size()];
            for (int i = 0; i < result.length; i++)
                result[i] = list.get(i);

            return result;
        }

        public byte[] getAddress() {
            return getByteArrayFromList(address);
        }

        public byte[] getData() {
            return getByteArrayFromList(data);
        }
    }
}
