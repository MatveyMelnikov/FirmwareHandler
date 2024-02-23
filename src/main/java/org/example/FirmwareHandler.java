package org.example;

import com.fazecast.jSerialComm.SerialPort;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

public class FirmwareHandler {
    static final ArrayList<FirmwareCommand> microprogram =
            new ArrayList<>();
    static int offsetAddress;
    static final ByteBuffer byteBuffer = ByteBuffer.allocate(4);
    static final byte[] buffer = new byte[10];
    static final byte ACK = 0x55;
    static final byte NACK = (byte)0xaa;
    static final int PART_SIZE = 256;
    static boolean fileEnd = false;
    public static final byte[] endOfWrite = { 0x33, (byte)0xcc, 0x0, 0x0 };

    static ArrayList<Byte> readBytesFromStream(
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

    // Little endian to big endian
    static int getLowPartOfAddress(List<Byte>bytes) {
        return ((bytes.get(0) & 0xff) << 8) | (bytes.get(1) & 0xff);
    }

    // Big endian
    static List<Byte> getAddressFromInt(int num) {
        List<Byte> result = new ArrayList<>();

        byteBuffer.clear();
        byteBuffer.putInt(num);

        byte[] byteArray = byteBuffer.array();
        for (byte b : byteArray)
            result.add(b);

        return result;
    }

    static boolean isErrorStatus(SerialPort port) {
        do {
            port.readBytes(buffer, 1);
        } while (!(buffer[0] == ACK || buffer[0] == NACK));

        return buffer[0] == NACK;
    }

    static void safeSkip(InputStream in, long n) throws IOException {
        long skipped = in.skip(n);
        if (skipped != n)
            throw new IOException("Skip error");
    }

    static void sendCommand(
            SerialPort port,
            FirmwareHandler.FirmwareCommand command
    ) throws IOException {
        port.writeBytes(command.getAddress(), 4);
        port.writeBytes(command.getData(), command.getData().length);
        if (isErrorStatus(port))
            throw new IOException("Response is not ACK");
    }

    static List<Byte> readHeader(InputStream in) throws IOException {
        // Skip ':' - start of data line in IntelHEX
        safeSkip(in, 1);

        return readBytesFromStream(in, 4);
    }

    static List<FirmwareCommand> getMicroprogramPart(
            InputStream in
    ) throws IOException {
        List<Byte> input;
        int readedBytes = 0;

        microprogram.clear();
        while (true) {
            input = readHeader(in);
            Byte type = input.get(3);
            if (type == 0x01 || type == 0x05) { // End of file or start address
                fileEnd = true;
                break;
            }
            if (type != 0x0) { // Extended linear address
                safeSkip(in, input.get(0) * 2 + 4);
                continue;
            }

            int address = getLowPartOfAddress(input.subList(1, 4)) +
                    offsetAddress;

            // We split the data into 2 bytes
            List<Byte> data = readBytesFromStream(in, input.get(0));
            for (int i = 0; i < input.get(0) / 2; i++) {
                int addressOffset = 2 * i;
                FirmwareCommand command = new FirmwareCommand(
                    getAddressFromInt(address + addressOffset),
                    3,
                    data.subList(addressOffset, addressOffset + 2)
                );
                microprogram.add(command);
            }

            safeSkip(in, 4);
            readedBytes += input.get(0);
            if (readedBytes >= PART_SIZE)
                break;
        }

        return microprogram;
    }

    public static int[] getFirstAndLastAddresses(
            InputStream in
    ) throws IOException {
        List<Byte> input;
        int firstAddress = 0;
        int lastAddress = 0;
        int payloadChars;
        boolean firstAddressSaved = false;

        while (true) {
            input = readHeader(in);
            Byte type = input.get(3);
            payloadChars = (input.get(0) & 0xff) * 2;

            if (type == 0x01 || type == 0x05) // end of file or start address
                break;
            if (type == 0x04) { // extended linear address
                offsetAddress = getLowPartOfAddress(
                        readBytesFromStream(in, input.get(0))
                ) << 16;
                // skip control sum + '\n'
                safeSkip(in, 4);
                continue;
            }

            if (firstAddressSaved)
                lastAddress = getLowPartOfAddress(input.subList(1, 4));
            else {
                firstAddressSaved = true;
                firstAddress = getLowPartOfAddress(input.subList(1, 4));
            }

            safeSkip(in, payloadChars + 4);
        }

        return new int[]{
                offsetAddress + firstAddress,
                offsetAddress + lastAddress
        };
    }

    static FirmwareCommand getEraseCommand(
            int[] addresses
    ) throws IOException {
        byte pagesToErase = (byte)(Math.ceil(
                (double)(addresses[1] - addresses[0]) / 1024
        ));

        return new FirmwareCommand(
                getAddressFromInt(addresses[0]),
                4,
                new ArrayList<>(List.of(pagesToErase))
        );
    }

    public static int transmitFirmware(
            SerialPort port,
            InputStream in,
            int[] addresses
    ) {
        fileEnd = false;
        byteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        int transmitted_commands = 0;
        List<FirmwareCommand> commands;
        FirmwareCommand eraseCommand;

        port.setComPortParameters(
                115200,
                8,
                SerialPort.ONE_STOP_BIT,
                SerialPort.NO_PARITY
        );
        port.openPort();
        port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);

        try {
            eraseCommand = FirmwareHandler.getEraseCommand(addresses);
            // Send erase command
            port.writeBytes(eraseCommand.command_type, 1);
            if (isErrorStatus(port))
                throw new IOException("Response is not ACK");
            sendCommand(port, eraseCommand);
            do {
                commands = getMicroprogramPart(in);

                port.writeBytes(commands.get(0).command_type, 1);
                if (isErrorStatus(port))
                    throw new IOException("Response is not ACK");

                for (FirmwareCommand command : commands) sendCommand(port, command);
                transmitted_commands += commands.size();

                port.writeBytes(FirmwareHandler.endOfWrite, 4);
            } while (!fileEnd);
        } catch (IOException e) {
            e.printStackTrace();
            return transmitted_commands * 2;
        }

        port.closePort();

        return transmitted_commands * 2;
    }

    static class FirmwareCommand {
        private final List<Byte> address;
        private final List<Byte> data;
        public byte[] command_type = new byte[1];

        FirmwareCommand(List<Byte> address, int command_type, List<Byte> data) {
            this.address = address;
            this.command_type[0] = (byte)(command_type + '0');
            this.data = data;
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
