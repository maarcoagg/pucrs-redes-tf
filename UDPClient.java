// Le uma linha do teclado
// Envia o pacote (linha digitada) ao servidor

import java.io.*; // classes para input e output streams e
import java.net.*;// DatagramaSocket,InetAddress,DatagramaPacket
import java.util.Scanner;

class UDPClient {
   public static void main(String args[]) throws Exception
   {      
      String filePath = selectFile();
      File selectedFile = new File(filePath);
      FileInputStream fis = new FileInputStream(selectedFile);
      int fileSize = fis.available();
      System.err.println("File size:"+fileSize);
      byte[] sendData = new byte[fileSize];
      //byte[] receiveData = new byte[1024];

      //contador de bytes
      for (int i = 0; i < fileSize; i++)
      {
         sendData[i] = (byte) fis.read();
      }

      // declara socket cliente
      DatagramSocket clientSocket = new DatagramSocket();

      // obtem endereco IP do servidor com o DNS
      InetAddress IPAddress = InetAddress.getByName("localhost");

      // cria pacote com o dado, o endereco do server e porta do servidor
      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, IPAddress, 9876);

      //envia o pacote
      clientSocket.send(sendPacket);

      System.out.println("Arquivo enviado com sucesso.");

      // fecha o cliente
      clientSocket.close();
      fis.close();
   }

   public static String selectFile()
   {
      // guarda nome dos arquivos para montar os paths
      final String tinyFileName = "file_less_then_1500bytes.txt";
      final String bigFileName = "file_more_then_10000bytes.txt";
      final String srcDir = System.getProperty("user.dir");

      // monta path dos arquivos a serem enviados
      String absoluteTinyFilePath = srcDir + File.separator + "send" + File.separator + tinyFileName;
      String absoluteBigFilePath = srcDir + File.separator + "send" + File.separator + bigFileName;
      String filePath = null;

      // pergunta ao cliente qual arquivo deseja enviar
      System.out.print("\t1 - " + tinyFileName + "\n" +
                        "\t2 - " + bigFileName + "\n"+
                        "Selecione qual arquivo enviar (1-2): ");
      Scanner input = new Scanner(System.in);
      Integer option = input.nextInt();

      if (option.equals(1))
         filePath = absoluteTinyFilePath;
      else if (option.equals(2))
         filePath = absoluteBigFilePath;
      else
      {
         System.err.println("Opcao invalida! Abortando execucao...");
         System.exit(1);
      }
      input.close();
      return filePath;
   }
}