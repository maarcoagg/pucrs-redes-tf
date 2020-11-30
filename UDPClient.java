import java.io.*; // classes para input e output streams e
import java.net.*;// DatagramaSocket,InetAddress,DatagramaPacket
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.zip.CRC32;
import java.security.MessageDigest;

class UDPClient {

   static CRC32 crc = new CRC32();
   static DatagramSocket socket; // UDP socket
   static InetAddress ip;  // IP servidor
   static int port = 9876; // Port servidor
   static int winSize = 1;
   static long sleepTime = 2000;
   static String checksum;
   static String winMode = "SS";
   final static String separator = ";";
   final static String sourceDir = System.getProperty("user.dir") + File.separator + "send";

   public static void main(String args[]) throws Exception
   {            
      ip = InetAddress.getByName("localhost");
      socket = new DatagramSocket();
      socket.setSoTimeout(10000); // Time-out: 10 seg
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
         {
            System.out.println("Enviando CHECKSUM para o servidor: "+checksum+"\n");
            String msg = "CHECKSUM" + separator + checksum;
            send(msg);
            
            /* Recebe checksum do server*/

            DatagramPacket recvPacket = receive(msg);
            msg = cleanMessage(recvPacket);
            String[] recvData = msg.split(separator);
            if (recvData.length == 2 && recvData[0].equals("CHECKSUM"))
            {
               String serverChecksum = recvData[1];
               if (checksum.equals(serverChecksum))
                  System.out.println("Arquivo enviado com sucesso :)\n");
               else
                  System.err.println("Erro ao enviar arquivo :(\n");
            }
         }
         else
            System.err.println("Erro ao enviar arquivo :(\n");
         
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
      winSize = 1; // Janela de congestionamento
      winMode = "Slow Start"; //Slow Start
      int seq = 0;  // Pacotes enviados
      byte[] data = null;

      boolean transfered = false;
      /* Enquanto todos pacotes não forem enviados */
      while (seq < f.size())
      {
         System.out.println("Window size: "+winSize+" | Window mode: "+winMode+".\n");
         for(int i = 0; i < winSize; i++)
         {
            if (seq+i >= f.size())
               break;
            data = f.get(seq+i); 
            System.out.println("Enviando pacote "+(seq+i)+"...");
            send(data);
         }

         /* Aguarda ACKs do servidor*/
         for(int i = 0; i < winSize; i++)
         {
            if (seq+i >= f.size())
               break;
            DatagramPacket recvPacket = receive(data);
            String msg = cleanMessage(recvPacket);
            String[] recvData = msg.split(separator);
            if (recvData.length == 2 && recvData[0].equals("ACK"))
            {
               if (Integer.parseInt(recvData[1]) == seq+1)
                  seq++;
               else
                  System.err.println("ACK esperado:"+(seq+1)+"\tAck recebido: "+Integer.parseInt(recvData[1]));
            } else System.err.println("Resposta ACK inválida do servidor:\n"+msg);
         }

         /* Se todos pacotes enviados foram confirmados, aumenta janela */
         /*
         System.out.println("Packets Sent: "+sent+"\tPackets Confirmed: "+confirmed+"\tSEQ: "+seq+"\n");
         
         if (confirmed == winSize)
            winSize = manageWindow();
         else
            if (winMode.equals("SS"))
            {
               winSize /= 2;
               winMode = "CA";
               winSize = manageWindow();
            }
         */
        
         /* Se último pacote foi confirmado, arquivo é dado como transferido. */
         if (seq == f.size())
            transfered = true;
      }
      return transfered;
   }

   /*private static int manageWindow()
   {
      final int maxSize = 16;
      int size;
      switch(winMode)
      {
         case "SS": 
            size = 2*winSize;
            if (size > maxSize)
               return maxSize;
            return size;
         case "CA": 
            size = winSize+1;
            if (size > maxSize)
               return maxSize;
            return size;
         case "FR":
            winSize = winSize/2;
            winMode = "CA";
            return winSize;
         default:
            System.err.println("Window Tag desconhecida.");
            return winSize;
      }
   }*/

   private static String selectFile()
   {
      File f = new File(sourceDir);
      String[] files = f.list();
      StringBuilder sb = new StringBuilder("0 - Gerar arquivo aleatório\n");

      for (int i = 0; i < files.length; i++)
         sb.append(i+1).append(" - ").append(files[i]).append("\n");
      System.out.print(sb.toString());
      
      String selectedPath = null;
      String fileName = null;
      Scanner s = new Scanner(System.in);
      do
      {
         System.out.print("Selecione arquivo para enviar (0-"+(files.length)+"): ");    
         Integer userInput = s.nextInt();
         
         if (userInput < 0 || userInput > files.length)
            System.err.println("Escolha um arquivo válido! ");
         else 
         {
            if (userInput == 0)
               fileName = generateFile(); // cria arquivo e retorna nome
            else
               fileName = files[userInput-1];
            selectedPath = sourceDir + File.separator + fileName;
            System.out.println("\nSelecionado o arquivo: "+fileName+".");
         }
      } while (selectedPath == null);
      s.close();
      
      try {
         MessageDigest md5Digest = MessageDigest.getInstance("MD5");
         checksum = getFileChecksum(md5Digest,new File(selectedPath));
         System.out.println("\nChecksum do arquivo "+fileName+": "+checksum);
         sleep(sleepTime);
      } catch (Exception e) {
         System.err.println("ERRO: "+e);
         System.exit(1);
      }
      return selectedPath;
   }

   private static String generateFile()
   {
      String fileName = generateName();
      final String absoluteFilePath = sourceDir + File.separator + fileName;
      FileOutputStream fos;
      try {
         fos = new FileOutputStream(absoluteFilePath);
         System.out.println("Arquivo \""+fileName+"\" criado.");

         int max = 3000;
         int min = 300;
         double random = Math.random() * (max  - min + 1) + min;
         int size = (int) random;
         System.out.println("Tamanho escolhido: "+size+" bytes.");

         max = 126;
         min = 60;
         for(int i = 0; i < size; i++ )
         {
            random = Math.random() * (max  - min + 1) + min;
            int num = (int) random;
            char c = (char) num;
            byte b = (byte) c;
            try{
               fos.write(b);
            } catch (IOException e) {
               System.err.println("I/O: "+e);
               System.exit(1);
            }
         }
         System.out.println("Conteudo de \""+fileName+"\" criado.");
      } catch (FileNotFoundException e) {
         System.err.println("ERRO: "+e);
         fos = null;
      }

      return fileName;
   }

   private static String generateName()
   {
      int max = 10000;
      int min = 1000;
      double random = Math.random() * (max  - min + 1) + min;
      int num = (int) random;
      StringBuilder sb = new StringBuilder("file_").
                           append(num).
                           append(".txt");
      
      return sb.toString();
   }

   private static String cleanMessage(DatagramPacket p)
   {
      byte[] cleanData = new byte[p.getLength()];

      for (int i = 0; i < p.getLength(); i++)
         cleanData[i] = p.getData()[i];
      
      String cleanMessage = new String(cleanData);
      System.out.println("Recebido de "+ip+":"+port+" a mensagem ("+p.getLength()+" bytes): "+cleanMessage+"\n");
      sleep(sleepTime);
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

      sleep(sleepTime);

      byte[] sendData;
      byte[] headerBytes;
      byte[] dataBytes;
      long crcVal;
      /* Mapeia header + pacotes de 300 bytes */
      int readedData = 0;
      try {
         for(int i = 0; i < filePackets; i++)
         {
            /* Cria pacote de 300 bytes de dados*/ 
            int dataSize = 0;   
            dataBytes = new byte[packetSize];

            if (i < filePackets-1) //se pacote não for o ultimo
            {
               dataSize = 300;
               for(int j = 0; j < dataSize; j++) //le 300 bytes de dados
               {
                  dataBytes[j] = (byte) fis.read();
                  readedData++;
               }
            } 
            else
            {
               dataSize = fileSize - readedData;
               for(int j = 0; j < dataSize; j++) //le N bytes de dados
               {
                  dataBytes[j] = (byte) fis.read();
                  readedData++;
               }

               System.out.println("\nÚltimo pacote ("+dataSize+"/"+dataBytes.length+" bytes): "+new String(dataBytes));
            }

            /* Calcula CRC*/
            crc.update(dataBytes);
            crcVal = crc.getValue();

            /* Cria cabeçalho em bytes*/
            String header = flag+separator+(seq++)+separator+crcVal+separator;
            headerBytes = header.getBytes();
            int headerSize = headerBytes.length;

            /* Cria pacote de envio em bytes */
            sendData = new byte[headerSize+packetSize];

            /* Adiciona cabeçalho */
            for(int j = 0; j < headerSize; j++)
               sendData[j] = headerBytes[j];

            /* Adiciona dados */
            int aux = headerSize;
            for(int j = 0; j < packetSize; j++)
               sendData[aux++] = dataBytes[j];

            /* Adiciona no dicionario*/
            fileMap.put(i, sendData);

            System.out.println("CRC do pacote #"+(i+1)+": "+crcVal);
         }
         System.out.println("Pacotes criados.");
         sleep(sleepTime);
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
         
         sleep(sleepTime);

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
         
         sleep(sleepTime);

         /* Envia FIN */
         seq = 0;
         msg = "FIN"+separator+seq;
         send(msg);

         /* Aguarda resposta do servidor */
         System.out.println("\nEsperando ACK...");
         recvPacket = receive(msg);
         msg = cleanMessage(recvPacket);
         
         sleep(sleepTime);
        
         /* Verifica se recebeu ACK corretamente */
         data = msg.split(separator);
         if (data.length == 2 && data[0].equals("ACK") && Integer.parseInt(data[1]) == seq+1)
         {
            svAck = Integer.parseInt(data[1]);

            /* Aguarda FIN-ACK do servidor */
            System.out.println("Esperando FIN-ACK...");
            recvPacket = receive(msg);
            msg = cleanMessage(recvPacket);
            
            sleep(sleepTime);
            
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
            } else System.err.println("Resposta FIN-ACK inválida do servidor!\n"+msg);
         } else System.err.println("Resposta ACK inválida do servidor:\n"+msg);
         attempts++;
      }while(connected);

      return connected;
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

   private static String getFileChecksum(MessageDigest digest, File file) throws IOException
   {
      FileInputStream fis = new FileInputStream(file);
      
      byte[] byteArray = new byte[1024];
      int bytesCount = 0; 
         
      while ((bytesCount = fis.read(byteArray)) != -1) {
         digest.update(byteArray, 0, bytesCount);
      }
      
      fis.close();
      
      byte[] bytes = digest.digest();
      
      StringBuilder sb = new StringBuilder();
      for(int i=0; i< bytes.length ;i++)
      {
         sb.append(Integer.toString((bytes[i] & 0xff) + 0x100, 16).substring(1));
      }
      
      //return complete hash
      return sb.toString();
   }

   private static void sleep(long ms)
   {
      try{
         Thread.sleep(ms);
      } catch(Exception e) {
         
      }
   }
}