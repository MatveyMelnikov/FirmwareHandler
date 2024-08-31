# FirmwareHandler
A simple class for converting a firmware file (IntelHEX) into a sequence of commands and sending it to the bootloader on the STM32. 


## Using
* Add a library for working with half-serial ports to your project - [jSerialComm](https://github.com/Fazecast/jSerialComm);
* Copy a [class](https://github.com/MatveyMelnikov/FirmwareHandler/blob/master/src/main/java/org/example/FirmwareHandler.java) to work with firmware;
* Open a file stream and calculate the start and end addresses:
```
FileInputStream fis = new FileInputStream(file);
int[] addresses = FirmwareHandler.getFirstAndLastAddresses(fis);
fis.close();
```
* Reopen the stream and call the main method for sending the firmware. The function returns the number of bytes sent by STM32:
```
fis = new FileInputStream(file);
int bytes = FirmwareHandler.transmitFirmware(selected_port, fis, addresses);
```


## Launch
```
java -cp SerialPortTest.jar org.example.Main
```
or with the port number and path to the firmware file:
```
java -cp SerialPortTest.jar org.example.Main 2 ../../SimpleAppWithBootloader.hex
```


## Links

* [Bootloader](https://github.com/MatveyMelnikov/Bootloader) - the bootloader that the current program is running with.

## License

[Licensed via CC BY-NC-ND 4.0](https://creativecommons.org/licenses/by-nc-nd/4.0/)
