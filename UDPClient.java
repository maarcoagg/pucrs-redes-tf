// Le uma linha do teclado
// Envia o pacote (linha digitada) ao servidor

import java.io.*; // classes para input e output streams e
import java.net.*;// DatagramaSocket,InetAddress,DatagramaPacket
import java.util.Scanner;

class UDPClient {
   public static void main(String args[]) throws Exception
   {      
      final int packetSize = 300;
      
      DatagramSocket clientSocket = new DatagramSocket();   // UDP socket
      InetAddress IPAddress = InetAddress.getByName("localhost"); // IP destino

      String filePath = selectPath();
      File selectedFile = new File(filePath);
      FileInputStream fis = new FileInputStream(selectedFile);
      int fileSize = fis.available();
      
      /* Cria um array de bytes do tamanho do arquivo*/
      System.out.println("Tamanho do arquivo: "+fileSize+" bytes");
      byte[] sendData = new byte[packetSize];

      /* Calcula a quantidade de pacotes necessária para enviar o arquivo completo */
      int filePackets = (int) Math.ceil((double)fileSize/packetSize);
      System.out.println("Pacotes necessarios: "+filePackets+" pacotes");

      
      int readedData = 0;
      /* Transfere arquivo em i pacotes */
      for (int i = 1; i <= filePackets; i++)
      {
         int sendSize = 0;
         /* Preenche pacote com dados */
         if (i < filePackets)
         {
            sendSize = 300;
            for (int j = 0; j < packetSize; j++)
            {
               sendData[j] = (byte) fis.read();
               readedData++;
            }
         }
         else
         {
            /* O ultimo pacote pode ter menos de 300 bytes  */
            sendSize = fileSize - readedData;

            /* Preenche sendData com o que resta do arquivo */
            for (int j = 0; j < sendSize; j++)
            {
               sendData[j] = (byte) fis.read();
               readedData++;
            }
            /* Preenche o restante do pacote com vazio */
            for (int j = sendSize; j < packetSize; j++)
               sendData[j] = Byte.parseByte(" ");
         }

         /* Cria pacote UDP e envia ao destino */
         DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9876);
         clientSocket.send(sendPacket);
         System.out.println("Pacote "+i+" enviado. Dados: "+sendSize+" bytes.");
      }
      
      System.out.println("Arquivo enviado com sucesso.");

      // fecha o cliente
      clientSocket.close();
      fis.close();
   }

   public static String selectPath()
   {
      final String fileName = "target.txt";
      final String sourceDir = System.getProperty("user.dir");
      String absolutePath = sourceDir + File.separator + "send" + File.separator + fileName;
      
      String choosenPath = null;
      System.out.print("\t1 - " + fileName + "\n" +
                        "Selecione qual arquivo enviar (1-1): ");
      Scanner userInput = new Scanner(System.in);
      Integer option = userInput.nextInt();

      if (option.equals(1))
         choosenPath = absolutePath;
      else
      {
         System.err.println("Opcao invalida! Abortando execucao...");
         System.exit(1);
      }
      userInput.close();
      return choosenPath;
   }
}