// Le uma linha do teclado
// Envia o pacote (linha digitada) ao servidor

import java.io.*; // classes para input e output streams e
import java.net.*;// DatagramaSocket,InetAddress,DatagramaPacket
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

class UDPClient {

   static DatagramSocket socket; // UDP socket
   static InetAddress ip;  // IP servidor
   static int port = 9876; // Port servidor
   final static String separator = ";";

   public static void main(String args[]) throws Exception
   {            
      ip = InetAddress.getByName("localhost");
      socket = new DatagramSocket();
      socket.setSoTimeout(5000); // Time-out: 5 seg
      boolean connected = false;
      Map<Integer,byte[]> fileMap;

      /* Define arquivo para enviar ao destino */
      String filePath = selectFile();

      /* Mapeia arquivo em pacotes de 300 bytes */
      fileMap = mapFile(filePath);

      /* Tenta 3-Way Handshake */
      connected = tryConnect();

      if (connected)
      {
         /* Transfere arquivo para servidor */
         if(transfer(fileMap))
            System.out.println("Arquivo enviado com sucesso.");
         else
            System.err.println("Erro ao enviar arquivo. :(");
         
         /* Tenta FIN-ACK Handshake */ 
         connected = tryDisconnect();
      }
      else 
      {
         System.err.println("Erro ao conectar. :(");
         System.exit(1);
      }
      socket.close();
   }

   private static boolean transfer(Map<Integer,byte[]> f)
   {
      boolean transfered = false;
      for(int i = 0; i < f.size(); i++)
      {
         boolean success = false;
         int attempts = 0;
         do
         {
            /* Envia pacote i ao servidor*/
            byte[] data = f.get(i);
            send(data);

            /* Aguarda ACK i+1 do servidor */
            System.out.println("Aguardando ACK...");
            DatagramPacket recvPacket = receive(data);

            /* Exibe resposta do servidor */
            String msg = cleanMessage(recvPacket);

            /* Verifica se recebeu ACK corretamente */
            String[] recvData = msg.split(separator);
            if (recvData.length == 2 && recvData[0].equals("ACK") && Integer.parseInt(recvData[1]) == i+1)
               success = true;
            else System.err.println("Resposta ACK inválida do servidor.\n");
            attempts++;
         } while(!success & attempts < 3);
         
         if (attempts >= 3)
            break;

         if (i == f.size()-1 && success)
            transfered = true;
      }
      return transfered;
   }

   private static String selectFile()
   {
      final String sourceDir = System.getProperty("user.dir") + File.separator + "send";
      File f = new File(sourceDir);
      String[] files = f.list();
      StringBuilder sb = new StringBuilder();

      for (int i = 0; i < files.length; i++)
         sb.append(i).append(" - ").append(files[i]).append("\n");
      System.out.print(sb.toString());
      
      String selectedPath = null;
      Scanner s = new Scanner(System.in);
      do
      {
         System.out.print("Selecione arquivo para enviar (0-"+(files.length-1)+"): ");    
         Integer userInput = s.nextInt();
         
         if (userInput < 0 || userInput >= files.length)
            System.err.println("Arquivo invalido! ");
         else
         {
            String fileName = files[userInput];
            selectedPath = sourceDir + File.separator + fileName;
            System.out.println("\nSelecionado o arquivo: "+fileName+".");
         }
      } while (selectedPath == null);
      s.close();
      return selectedPath;
   }

   private static String cleanMessage(DatagramPacket p)
   {
      byte[] cleanData = new byte[p.getLength()];

      for (int i = 0; i < p.getLength(); i++)
         cleanData[i] = p.getData()[i];
      
      String cleanMessage = new String(cleanData);
      System.out.println("Recebido de "+ip+":"+port+" a mensagem ("+p.getLength()+" bytes): "+cleanMessage+"\n");
      return cleanMessage;
   }

   /**
    * Divide o arquivo em 'filePath' em N pacotes de 300 bytes
    * @param filePath   local do arquivo 
    * @param separator  separador de dados
    * @return  Dicionario <num_pacote,dados>
    */
   private static Map<Integer,byte[]> mapFile(String filePath)
   {
      Map<Integer,byte[]> fileMap = new HashMap<>();
      File selectedFile = new File(filePath);
      FileInputStream fis = null;
      final int packetSize = 300;
      String flag = "DATA";
      int fileSize = 0;
      int seq = 0;

      try {
         fis = new FileInputStream(selectedFile);
         fileSize = fis.available();
      } catch (FileNotFoundException e) {
         System.err.println(e);
         System.exit(1);
      } catch (IOException e) {
         System.err.println(e);
         System.exit(1);
      }
            
      /* Calcula a quantidade de pacotes necessária para enviar o arquivo completo */
      int filePackets = (int) Math.ceil((double)fileSize/packetSize);
      System.out.println("Tamanho em bytes: "+fileSize);
      System.out.println("Pacotes necessarios: "+filePackets+"\n");

      /* Mapeia header + pacotes de 300 bytes */
      int readedData = 0;
      try {
         for(int i = 0; i < filePackets; i++)
         {
            int dataSize = 0;            
            /* Cria cabeçalho e add no pacote 'sendData' */
            String header = flag+separator+(seq++)+separator;
            byte[] headerBytes = header.getBytes();
            int headerSize = headerBytes.length;
            byte[] sendData = new byte[headerSize+packetSize];
            for(int j = 0; j < headerSize; j++)
               sendData[j] = headerBytes[j];
            
            /* Add dados no pacote 'sendData' */
            if (i < filePackets-1)
            {
               dataSize = 300;
               for (int j = headerSize; j < (headerSize+packetSize); j++)
               {
                  sendData[j] = (byte) fis.read();
                  readedData++;
               }
            }
            else
            {
               /* O ultimo pacote pode ter menos de 300 bytes  */
               dataSize = fileSize - readedData;

               /* Preenche sendData com o que resta do arquivo */
               for (int j = headerSize; j < headerSize+dataSize; j++)
               {
                  sendData[j] = (byte) fis.read();
                  readedData++;
               }
               /* Preenche o restante do pacote com vazio */
               for (int j = headerSize+dataSize; j < headerSize+packetSize; j++)
                  sendData[j] = Byte.parseByte("0");
            }
            fileMap.put(i, sendData);
         }
         System.out.println("Pacotes criados.");
      } catch (IOException e) {
         System.err.println(e);
         System.exit(1);
      }

      return fileMap;
   }

   private static boolean tryConnect()
   {
      boolean connected = false;
      int attempts = 1;      
      do
      {
         System.out.println("\nIniciando tentativa #"+attempts+" de conexão com servidor...");
         
         /* Envia SYN */
         int seq = 0;
         String msg = "SYN"+separator+seq;
         send(msg);

         /* Aguarda resposta do servidor */
         System.out.println("\nEsperando SYN-ACK...");
         DatagramPacket recvPacket = receive(msg);

         /* Exibe resposta do servidor */
         msg = cleanMessage(recvPacket);

         /* Verifica se recebeu SYN-ACK corretamente */
         String[] data = msg.split(separator);
         if (data.length == 3 && data[0].equals("SYN-ACK") && Integer.parseInt(data[2]) == seq+1)
         {
            /* Envia ACK */
            int ack = Integer.parseInt(data[1])+1;
            msg = "ACK;"+ack;
            send(msg);

            System.out.println("Conexão estabelecida.\n");
            connected = true;
         } else System.err.println("Resposta inválida do servidor!\n");
         
         attempts++;
      } while(!connected && attempts <= 3);

      return connected;
   }

   private static boolean tryDisconnect()
   {
      DatagramPacket recvPacket = null;
      boolean connected = true;
      String[] data = null;
      String msg = null;
      int attempts = 1;
      int svSeq = -1;
      int svAck = -1;
      int seq = -1;
      int ack = -1;

      do
      {
         System.out.println("Iniciando tentativa #"+attempts+" de encerrar conexão com servidor.\n");

         /* Envia FIN */
         seq = 0;
         msg = "FIN"+separator+seq;
         send(msg);

         /* Aguarda resposta do servidor */
         System.out.println("\nEsperando ACK...");
         recvPacket = receive(msg);
         msg = cleanMessage(recvPacket);
         
         /* Verifica se recebeu ACK corretamente */
         data = msg.split(separator);
         if (data.length == 2 && data[0].equals("ACK") && Integer.parseInt(data[1]) == seq+1)
         {
            svAck = Integer.parseInt(data[1]);

            /* Aguarda FIN-ACK do servidor */
            System.out.println("Esperando FIN-ACK...");
            recvPacket = receive(msg);
            msg = cleanMessage(recvPacket);
            
            /* Verifica se recebeu FIN-ACK corretamente */
            data = msg.split(separator);
            if (data.length == 3 && data[0].equals("FIN-ACK") && Integer.parseInt(data[2]) == svAck)
            {
               svSeq = Integer.parseInt(data[1]);
               /* Envia ACK */
               ack = svSeq+1;
               msg = "ACK;"+ack;
               send(msg);

               /* Encerra conexão */
               System.out.println("Conexão encerrada.\n");
               connected = false;
            } else System.err.println("Resposta FIN-ACK inválida do servidor!\n");
         } else System.err.println("Resposta ACK inválida do servidor!\n");
         attempts++;
      }while(connected && attempts <= 3);

      return false;
   }

   private static void send(String msg)
   {
      byte[] sendData = msg.getBytes();
      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, port);

      System.out.println("Enviando "+sendData.length+" bytes para "+ip+":"+port+".");
      try{
         socket.send(sendPacket);
      } catch (Exception e) {
         System.err.println("ERRO: Não foi possível enviar pacote");
         System.err.println(e);
      }
   }

   private static void send(byte[] sendData)
   {
      DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, ip, port);
      if (sendData.length < 300)
         System.out.println("Enviando para "+ip+":"+port+" ("+sendData.length+" bytes): "+new String(sendData)+".");
      else
         System.out.println("Enviando "+sendData.length+" bytes para "+ip+":"+port+".");
         
      try{
         socket.send(sendPacket);
      } catch (Exception e) {
         System.err.println("ERRO: Não foi possível enviar pacote");
         System.err.println(e);
      }
   }

   /**
    * Recebe pacote UDP de até 1 Kb. Retransmite 'msg' caso uma exceção ocorra.
    * @param msg Mensagem a ser retransmitida
    * @return  Pacote UDP recebido.
    */
   private static DatagramPacket receive(String msg)
   {
      DatagramPacket recvPacket = null;
      byte[] recvData = new byte[1024];
      
      recvPacket = new DatagramPacket(recvData, recvData.length);
      try {
         socket.receive(recvPacket);
      } catch (SocketTimeoutException e) {
         System.err.println("TIMEOUT: Retransmitindo mensagem.");
         send(msg);
      } catch (IOException e){
         System.err.println("I/O: Retransmitindo mensagem");
         send(msg);
      }

      return recvPacket;
   }

   private static DatagramPacket receive(byte[] data)
   {
      DatagramPacket recvPacket = null;
      byte[] recvData = new byte[1024];
      
      recvPacket = new DatagramPacket(recvData, recvData.length);
      try {
         socket.receive(recvPacket);
      } catch (SocketTimeoutException e) {
         System.err.println("TIMEOUT: Retransmitindo mensagem.");
         send(data);
      } catch (IOException e){
         System.err.println("I/O: Retransmitindo mensagem");
         send(data);
      }

      return recvPacket;
   }
}