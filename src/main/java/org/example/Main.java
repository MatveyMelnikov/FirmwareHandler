package org.example;
import com.fazecast.jSerialComm.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Scanner;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {
    static char prev_symbol = 0;
    static SerialPort selected_port = null;
    static byte[] buffer = new byte[10];
    static final byte ACK = 0x55;
    static final byte NACK = (byte)0xaa;

    public static void main(String[] args) throws Exception {
        Scanner scanner = new Scanner(System.in);
        SerialPort[] ports = SerialPort.getCommPorts();

        System.out.println("-Ports:");
        for (int i = 0; i < ports.length; i++) {
            System.out.printf(
                    "%d - %s - %s\n",
                    i + 1,
                    ports[i].getDescriptivePortName(), ports[i].getSystemPortName()
            );
        }

        if (args.length != 0) {
            selected_port = ports[Integer.parseInt(args[0]) - 1];
            System.out.printf(
                    "Selected port: %s\n",
                    selected_port.getSystemPortName()
            );
        } else {
            int port_num = scanner.nextInt();
            selected_port = ports[port_num - 1];
        }

        selected_port.setComPortParameters(
                115200,
                8,
                SerialPort.ONE_STOP_BIT,
                SerialPort.NO_PARITY
        );
        selected_port.openPort();





        selected_port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
//        selected_port.readBytes(buffer, buffer.length);
//
//        for (byte b : buffer)
//            System.out.print((char) b);

        //print_input();

//        while (true) {
//            String input = scanner.nextLine();
//            if (Objects.equals(input, "close"))
//                break;
//
//            if (input.isEmpty() || !output_command(input.charAt(0)))
//                continue;
//            print_input();
//        }


        //String path = scanner.nextLine();
        String path = "/home/mathias/Documents/Projects/STM32Projects/" +
                "SimpleAppWithBootloader/build/SimpleAppWithBootloader.hex";
        File file = new File(path);
        FileInputStream fis = new FileInputStream(file);

        List<Firmware_Handler.Firmware_Command> commands =
                Firmware_Handler.getMicroprogram(fis);
        Firmware_Handler.Firmware_Command eraseCommand =
                Firmware_Handler.getEraseCommand();

        // Send erase command
        //send_command(eraseCommand);
        for (Firmware_Handler.Firmware_Command command : commands)
            send_command(command);

//        buffer[0] = 4 + '0';
//        // Send command num
//        selected_port.writeBytes(buffer, 1);
//        // Send start erase address
//        selected_port.writeBytes(eraseCommand.getAddress(), 4);
//        if (!receive_status())
//            throw new Exception("Response is not ACK");
//        // Send pages num to erase
//        selected_port.writeBytes(eraseCommand.getData(), 1);
//        if (!receive_status())
//            throw new Exception("Response is not ACK");
//        if (!receive_status())
//            throw new Exception("Response is not ACK");



//        ByteBuffer b = ByteBuffer.allocate(4);
//        b.order(ByteOrder.LITTLE_ENDIAN); // optional, the initial order of a byte buffer is always BIG_ENDIAN.
//
//        buffer[0] = 3 + '0';
//        selected_port.writeBytes(buffer, 1);
//
//        b.putInt(0x08000000 + 0x2800);
//        byte[] addr = b.array();
//        selected_port.writeBytes(addr, 4);
//
//        if (!receive_status())
//            throw new Exception("Response is not ACK");
//
//        b.clear();
//        b.putInt(0xaa55);
//        byte[] data = b.array();
//        selected_port.writeBytes(data, 4);
//        if (!receive_status())
//            throw new Exception("Response is not ACK");
//
//        if (!receive_status())
//            throw new Exception("Response is not ACK");

        selected_port.closePort();
    }
    public static boolean check_prompt(char symbol) {
        if (symbol == 0 && prev_symbol == '>')
            return true;
        prev_symbol = symbol;
        return false;
    }

    public static void print_input() {
        selected_port.setComPortTimeouts(SerialPort.TIMEOUT_READ_BLOCKING, 0, 0);
        InputStream in = selected_port.getInputStream();
        char symbol = 0;
        try
        {
            do {
                symbol = (char)in.read();
                if (symbol == 0)
                    continue;
                System.out.print(symbol);
            } while (!check_prompt(symbol));
            in.close();
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static boolean receive_status() {
        do {
            selected_port.readBytes(buffer, 1);
        } while (!(buffer[0] == ACK || buffer[0] == NACK));

        return buffer[0] == ACK;
    }

    public static boolean output_command(char command) {
        byte command_num = (byte)(command - '0');
        if (command_num < 0 || command_num > 5)
            return false;

        buffer[0] = (byte)(command);
        selected_port.writeBytes(buffer, 1);

        return true;
    }

    public static void send_command(
            Firmware_Handler.Firmware_Command command
    ) throws Exception {
        // Send command num
        selected_port.writeBytes(command.command_type, 1);
        // Send start erase address
        selected_port.writeBytes(command.getAddress(), 4);
        if (!receive_status())
            throw new Exception("Response is not ACK");
        // Send pages num to erase
        selected_port.writeBytes(command.getData(), command.getData().length);
        if (!receive_status())
            throw new Exception("Response is not ACK");
        if (!receive_status())
            throw new Exception("Response is not ACK");
    }
}