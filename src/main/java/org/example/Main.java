package org.example;
import com.fazecast.jSerialComm.*;

import java.io.File;
import java.io.FileInputStream;
import java.util.Scanner;

public class Main {
    static SerialPort selected_port = null;

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
            System.out.println("Enter port num:");
            int port_num = scanner.nextInt();
            selected_port = ports[port_num - 1];
        }

        String path;
        if (args.length >= 2) {
            path = args[1];
            System.out.printf("Selected path: %s\n", path);
        } else {
            System.out.println("Enter file path (.hex):");
            do {
                path = scanner.nextLine();
            } while (path.isEmpty());
        }
        File file = new File(path);

        FileInputStream fis = new FileInputStream(file);
        int[] addresses = FirmwareHandler.getFirstAndLastAddresses(fis);
        fis.close();

        System.out.printf("Firmware size: ~%d bytes\n", addresses[1] - addresses[0]);
        System.out.printf(
                "Addresses: %s - %s\n",
                String.format("0x%08X", addresses[0]),
                String.format("0x%08X", addresses[1])
        );

        // You must create a new file stream
        fis = new FileInputStream(file);
        int bytes = FirmwareHandler.transmitFirmware(selected_port, fis, addresses);
        System.out.printf("Successfully transmitted: ~%d bytes\n", bytes);
    }
}
