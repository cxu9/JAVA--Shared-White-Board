package Server;


import java.awt.Color;
import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import PublishSubscribeSystem.ClientInfo;
import PublishSubscribeSystem.PublishSubscribeSystem;
import Shape.*;
import Utils.EncryptDecrypt;


public class Client_thread implements Runnable {



    private Socket clientsocket;
    private int clientnumber;
    private String username;
    private boolean isManager = false;
    private long time;


    Client_thread (Socket client,int clientnumber) throws IOException{
        this.clientsocket = client;
        this.clientnumber = clientnumber;

    }

    @Override
    public void run() {
        try(Socket socket = clientsocket){

            InputStream is = socket.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader ois = new BufferedReader(isr);
            OutputStreamWriter oos = new OutputStreamWriter(socket.getOutputStream());
            JSONParser parser = new JSONParser();

            String result;


            while(!socket.isClosed()){




                while((result = ois.readLine()) != null){

                        result = EncryptDecrypt.decrypt(result);
                        System.out.println("Received from client: "+" "+result);
                  	    ServerUI.messageAppender.appendToMessagePane(ServerUI.logPane, ServerUI.dtf.format(LocalDateTime.now()) + " | ", Color.WHITE, true);
                  	    ServerUI.messageAppender.appendToMessagePane(ServerUI.logPane, "Received from client: "+" "+result + "\n\n", Color.WHITE, true);

                        JSONObject command = (JSONObject) parser.parse(result);

                        if(command.get("Source").toString().equals("Client") && command.get("Goal").toString().equals("Draw"))
                        {
                            String obj = command.get("ObjectString").toString();
                            String type = command.get("Class").toString();
                            byte[] bytes= Base64.getDecoder().decode(obj);
                            Object object;

                            JSONObject reply = new JSONObject();
                            reply.put("Source","Server");
                            reply.put("Goal","Reply");
                            reply.put("ObjectString","Successfully received!");

                            String acknowledgement = EncryptDecrypt.encrypt(reply.toJSONString());

                            oos.write(acknowledgement+"\n");
                            oos.flush();

                            switch(type) {
                                case "Shape.MyLine":
                                    object = (MyLine) deserialize(bytes);
                                    PublishSubscribeSystem.getInstance().getBoardState().addShapes((MyShape) object);
                                    PublishSubscribeSystem.getInstance().broadcastShapes((MyShape) object,username);
                                    break;
                                case "Shape.MyEllipse":
                                    object = (MyEllipse) deserialize(bytes);
                                    PublishSubscribeSystem.getInstance().getBoardState().addShapes((MyShape) object);
                                    PublishSubscribeSystem.getInstance().broadcastShapes((MyShape) object,username);
                                    break;
                                case "Shape.MyRectangle":
                                    object = (MyRectangle) deserialize(bytes);
                                    PublishSubscribeSystem.getInstance().getBoardState().addShapes((MyShape) object);
                                    PublishSubscribeSystem.getInstance().broadcastShapes((MyShape) object,username);
                                    break;
                                case "Shape.MyText":
                                    object = (MyText) deserialize(bytes);
                                    PublishSubscribeSystem.getInstance().getBoardState().addShapes((MyShape) object);
                                    PublishSubscribeSystem.getInstance().broadcastShapes((MyShape) object,username);
                                    break;
                                default:
                                    break;
                            }
                        }
                        else if(command.get("Source").toString().equals("Client") && command.get("Goal").toString().equals("Create")) {
                            String username = command.get("Username").toString();
                            this.username = username;
                            JSONObject reply = new JSONObject();
                            reply.put("Source","Server");
                            reply.put("Goal","Create");

                            boolean res = false;

                            synchronized(PublishSubscribeSystem.getInstance()) {
                            if (!PublishSubscribeSystem.getInstance().hasManger()) {
                            	res = true;
                            	PublishSubscribeSystem.getInstance().setManager(command.get("Username").toString());
                            }
                            }

                            if(res) {
                            PublishSubscribeSystem.getInstance().registerClient(username, socket);
                            reply.put("ObjectString","Success");
                            this.isManager = true;
                            String acknowledgement = EncryptDecrypt.encrypt(reply.toJSONString());
                            oos.write(acknowledgement+"\n");
                            oos.flush();
                            }
                            else {
                            reply.put("ObjectString","Failure");
                            String acknowledgement = EncryptDecrypt.encrypt(reply.toJSONString());
                            oos.write(acknowledgement+"\n");
                            oos.flush();
                            oos.close();
                            }



                        }
                        else if(command.get("Source").toString().equals("Client") && command.get("Goal").toString().equals("Chat")) {
                            String username = command.get("Username").toString();
                            String message = command.get("Message").toString();
                            JSONObject reply = new JSONObject();


                                reply.put("Source","Server");
                                reply.put("Goal","Chat");
                                reply.put("message", message);
                                reply.put("username", username);
                            PublishSubscribeSystem.getInstance().broadcastJSON(reply,this.username);




                        }
                        // a user leaves the board and chat room (not being kicked out)
                        else if(command.get("Source").toString().equals("Client") && command.get("Goal").toString().equals("Leave")) {
                            String username = command.get("Username").toString();
                            this.username = username;
                            JSONObject reply = new JSONObject();
                            PublishSubscribeSystem.getInstance().deregisterClient(username);
                            reply.put("Source","Server");
                            reply.put("Goal","Leave");
                            reply.put("username", username);

                            PublishSubscribeSystem.getInstance().broadcastJSON(reply,this.username);
                            // Need to get somebody in the waiting list to get in！

                            LinkedBlockingQueue<ClientInfo> q = PublishSubscribeSystem.getInstance().getQueue();

                            synchronized(q) {
                                if(q.size()!=0 && PublishSubscribeSystem.getInstance().getUsermap().size() < PublishSubscribeSystem.getInstance().getRoomSize()) {

                                    ClientInfo clientinfo;

                                    clientinfo = q.poll();

                                    String name = clientinfo.getName();
                                    Socket s = clientinfo.getClient();
                                    PublishSubscribeSystem.getInstance().getUsermap().put(name, s);

                                    JSONObject updateUserList = new JSONObject();
                                    updateUserList.put("Source","Server");
                                    updateUserList.put("Goal","Enter");
                                    updateUserList.put("username",name);
                                    PublishSubscribeSystem.getInstance().broadcastJSON(updateUserList);

                                    JSONObject endwaiting = new JSONObject();
                                    endwaiting.put("Source","Server");
                                    endwaiting.put("Goal","Accept");
                                    endwaiting.put("Status","In_Room");
                                    BoardState obj1 = PublishSubscribeSystem.getInstance().getBoardState();
                                    String boarddstr = Base64.getEncoder().encodeToString(serialize(obj1));
                                    ArrayList<String> obj2 = PublishSubscribeSystem.getInstance().getUserList();

                                    endwaiting.put("BoardState", boarddstr);
                                    endwaiting.put("UserList",obj2);

                                    String endwaitingstr = EncryptDecrypt.encrypt(endwaiting.toJSONString());

                                    OutputStream aout = s.getOutputStream();
                                    OutputStreamWriter aoos =new OutputStreamWriter(aout, "UTF8");
                                    aoos.write(endwaitingstr+"\n");
                                    aoos.flush();




                                }
                            }




                        }

                        else if(command.get("Source").toString().equals("Client") && command.get("Goal").toString().equals("Close")) {

                            JSONObject reply = new JSONObject();

                            	reply.put("Source","Server");
                                reply.put("Goal","Close");
                                reply.put("ObjectString", "Manager " + username + " is closing the board");

                                PublishSubscribeSystem.getInstance().resetManager();

                                PublishSubscribeSystem.getInstance().broadcastJSON(reply,this.username);


                                LinkedBlockingQueue<ClientInfo> queue = PublishSubscribeSystem.getInstance().getQueue();


                                Iterator<ClientInfo> listOfClients = queue.iterator();
                                while (listOfClients.hasNext()) {
                                    ClientInfo current = listOfClients.next();
                                    Socket wait = current.getClient();
                                    if(!wait.isClosed()){
                                        OutputStream out = wait.getOutputStream();
                                        OutputStreamWriter woos =new OutputStreamWriter(out, "UTF8");
                                        woos.write(reply.toJSONString()+"\n");
                                        woos.flush();

                                    }
                                }

                                PublishSubscribeSystem.getInstance().disconnectServer();
//                            }


                        }


                        else if(command.get("Source").toString().equals("Client") && command.get("Goal").toString().equals("Remove")) {
                            String removename = command.get("Username").toString();
                            JSONObject reply = new JSONObject();

                            	boolean addsocket = false;
                            	if(PublishSubscribeSystem.getInstance().getUsermap().containsKey(removename)) {
                            		addsocket = true;
                            	}
                            	reply.put("Source","Server");
                                reply.put("Goal","Remove");
                                reply.put("ObjectString", "User " + removename + " has been kicked out");

                                PublishSubscribeSystem.getInstance().sendtoSpecificUser(reply,removename);

                                PublishSubscribeSystem.getInstance().deregisterClient(removename);


                                // after the remove we need to close the socket based on it

                                JSONObject broadcasttheremove = new JSONObject();

                                broadcasttheremove.put("Source","Server");
                                broadcasttheremove.put("Goal","Leave");
                                broadcasttheremove.put("message","User " + removename + " has been kicked out");
                                broadcasttheremove.put("username",removename);

                                PublishSubscribeSystem.getInstance().broadcastJSON(broadcasttheremove,this.username);

                                LinkedBlockingQueue<ClientInfo> q = PublishSubscribeSystem.getInstance().getQueue();

                                synchronized(q) {
                                	if(q.size()!=0 && addsocket && PublishSubscribeSystem.getInstance().getUsermap().size() < PublishSubscribeSystem.getInstance().getRoomSize()) {

                                		ClientInfo clientinfo;

                                		clientinfo = q.poll();

                                		String name = clientinfo.getName();
                                		Socket s = clientinfo.getClient();
                                		PublishSubscribeSystem.getInstance().getUsermap().put(name, s);

                                		JSONObject updateUserList = new JSONObject();
                                		updateUserList.put("Source","Server");
                                		updateUserList.put("Goal","Enter");
                                		updateUserList.put("username",name);
                                		PublishSubscribeSystem.getInstance().broadcastJSON(updateUserList);

                                        JSONObject endwaiting = new JSONObject();
                                        endwaiting.put("Source","Server");
                                        endwaiting.put("Goal","Accept");
                                        endwaiting.put("Status","In_Room");
                                        BoardState obj1 = PublishSubscribeSystem.getInstance().getBoardState();
                                        String boarddstr = Base64.getEncoder().encodeToString(serialize(obj1));
                                        ArrayList<String> obj2 = PublishSubscribeSystem.getInstance().getUserList();

                                        endwaiting.put("BoardState", boarddstr);
                                        endwaiting.put("UserList",obj2);

                                        String endwaitingstr = EncryptDecrypt.encrypt(endwaiting.toJSONString());

                                        OutputStream aout = s.getOutputStream();
                                        OutputStreamWriter aoos =new OutputStreamWriter(aout, "UTF8");
                                        aoos.write(endwaitingstr+"\n");
                                        aoos.flush();

                                }
                              }



                        }
                        else if(command.get("Source").toString().equals("Client") && command.get("Goal").toString().equals("New")) {


                            JSONObject reply = new JSONObject();

                            	reply.put("Source","Server");
                                reply.put("Goal","New");
                                reply.put("ObjectString", "Manager " + username + " has cleaned the board");

                                PublishSubscribeSystem.getInstance().resetBoardState();

                                PublishSubscribeSystem.getInstance().broadcastJSON(reply,this.username);
                                
                                JSONObject msg = new JSONObject();
                                msg.put("Source","Server");
                                msg.put("Goal","Chat");
                                msg.put("message", "The Board Owner clears the board!");
                                msg.put("username", "Board_Owner");
                                PublishSubscribeSystem.getInstance().broadcastJSON(msg);



                        }
                        else if(command.get("Source").toString().equals("Client") && command.get("Goal").toString().equals("Enter")) {
                            String username = command.get("Username").toString();
                            this.username = username;

                            boolean hasBoard = false;
                            boolean norepeatName =true;
                            
                            
                            synchronized(PublishSubscribeSystem.getInstance()) {
                                if (PublishSubscribeSystem.getInstance().hasManger()) {
                                    hasBoard = true;
                                }
                            }
                            
                            synchronized(PublishSubscribeSystem.getInstance()) {
                                if (PublishSubscribeSystem.getInstance().hasrepeatedName(username)) {
                                    norepeatName = false;
                                }
                            }



                            PublishSubscribeSystem.getInstance().getApplicants().put(username, socket);

                            if(hasBoard && norepeatName) {

                               
                                JSONObject reply = new JSONObject();
                                reply.put("Source", "Server");
                                reply.put("Goal", "Authorize");
                                reply.put("ObjectString", "Need to authorize the applicant");
                                reply.put("username", username);

                                PublishSubscribeSystem.getInstance().sendToManger(reply);


                            }
                            else if(!norepeatName){
                                JSONObject reply = new JSONObject();

                                reply.put("Source","Server");
                                reply.put("Goal","Reply");
                                reply.put("ObjectString","repeated Name, double check");

                                String message = EncryptDecrypt.encrypt(reply.toJSONString());

                                oos.write(message+"\n");
                                oos.flush();
                                PublishSubscribeSystem.getInstance().getApplicants().remove(username);
                                oos.close();


                            }
                            else{
                                JSONObject reply = new JSONObject();

                                reply.put("Source","Server");
                                reply.put("Goal","Reply");
                                reply.put("ObjectString","No board yet, try to create one");

                                String message = EncryptDecrypt.encrypt(reply.toJSONString());


                                oos.write(message+"\n");
                                oos.flush();
                                PublishSubscribeSystem.getInstance().getApplicants().remove(username);
                                oos.close();



                            }

                        }
                        else if(command.get("Source").toString().equals("Client") && command.get("Goal").toString().equals("Accept")) {
                        	String applicant = command.get("Username").toString();

                            JSONObject reply = new JSONObject();

                            	Socket client  = PublishSubscribeSystem.getInstance().getApplicants().get(applicant);

                            	if(!client.isClosed()) {
                            	boolean res = PublishSubscribeSystem.getInstance().registerClient(applicant, client);

                            	reply.put("Source","Server");
                                reply.put("Goal","Accept");
                                
                                PublishSubscribeSystem.getInstance().getApplicants().remove(applicant);
                                
                                if (res)
                                {
                                    BoardState obj1 = PublishSubscribeSystem.getInstance().getBoardState();
                                    String boarddstr = Base64.getEncoder().encodeToString(serialize(obj1));
                                    ArrayList<String> obj2 = PublishSubscribeSystem.getInstance().getUserList();

                                reply.put("BoardState", boarddstr);
                                reply.put("UserList",obj2);
                                reply.put("Status","In_Room");
                              

                                JSONObject updateUserList = new JSONObject();
                                updateUserList.put("Source","Server");
                                updateUserList.put("Goal","Enter");
                                updateUserList.put("username",applicant);
                                PublishSubscribeSystem.getInstance().broadcastJSON(updateUserList);

                                }
                                
                                else {
                                reply.put("message","the room is full, you are in the waiting list");
                                reply.put("Status","In_Queue");

                                }

                                String message = EncryptDecrypt.encrypt(reply.toJSONString());

                                OutputStream aout = client.getOutputStream();
                                OutputStreamWriter aoos =new OutputStreamWriter(aout, "UTF8");
                                aoos.write(message+"\n");
                                aoos.flush();

                            	}

                        }
                        else if(command.get("Source").toString().equals("Client") && command.get("Goal").toString().equals("Decline")) {
                        	String applicant = command.get("Username").toString();
                            JSONObject reply = new JSONObject();

                            	Socket client  = PublishSubscribeSystem.getInstance().getApplicants().get(applicant);
                            	PublishSubscribeSystem.getInstance().getApplicants().remove(applicant);
                            	
                            	if(!client.isClosed()) {                     
                            	reply.put("Source","Server");
                                reply.put("Goal","Decline");

                                OutputStream aout = client.getOutputStream();
                                OutputStreamWriter aoos =new OutputStreamWriter(aout, "UTF8");
                                String message = EncryptDecrypt.encrypt(reply.toJSONString());
                                aoos.write(message+"\n");
                                aoos.flush();
                                aoos.close();

                            	}
                        }
                        else if(command.get("Source").toString().equals("Client") && command.get("Goal").toString().equals("Load")) {

                            JSONObject reply = new JSONObject();
                            String boardstate = command.get("ObjectString").toString();

                            	reply.put("Source","Server");
                                reply.put("Goal","Load");
                                reply.put("ObjectString", boardstate);
                                PublishSubscribeSystem.getInstance().broadcastJSON(reply,this.username);

                                byte[] bytes = Base64.getDecoder().decode(boardstate);
                                BoardState bs = (BoardState) PublishSubscribeSystem.getInstance().deserialize(bytes);
                                PublishSubscribeSystem.getInstance().updateBoardState(bs);
                                JSONObject msg = new JSONObject();
                                msg.put("Source","Server");
                                msg.put("Goal","Chat");
                                msg.put("message", "The Board Owner load new shapes!");
                                msg.put("username", "Board_Owner");
                                PublishSubscribeSystem.getInstance().broadcastJSON(msg);
                                
                           }
                        else if(command.get("Source").toString().equals("Client") && command.get("Goal").toString().equals("Timeout")) {

                        	String user = command.get("Username").toString();
                        	PublishSubscribeSystem.getInstance().deregisterClient(user);
                        	if(PublishSubscribeSystem.getInstance().getApplicants().contains(user)) {
                        		PublishSubscribeSystem.getInstance().getApplicants().remove(user);
                        	}
                                
                           }
                        else if(command.get("Source").toString().equals("Client") && command.get("Goal").toString().equals("Withdraw")) {
                        	if(PublishSubscribeSystem.getInstance().getBoardState().getShapes().size()!=0) {
                        	PublishSubscribeSystem.getInstance().getBoardState().getShapes().remove(PublishSubscribeSystem.getInstance().getBoardState().getShapes().size()-1);
                        	JSONObject reply = new JSONObject();
                        	reply.put("Source","Server");
                            reply.put("Goal","Load");
                            BoardState bs = PublishSubscribeSystem.getInstance().getBoardState();
                            byte[] bytes = PublishSubscribeSystem.getInstance().serialize(bs);
                            String boardstate = Base64.getEncoder().encodeToString(bytes);
                            reply.put("ObjectString", boardstate);
                            PublishSubscribeSystem.getInstance().broadcastJSON(reply);

                        	}  
                           }
                        
                        }
            }

        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();

        }
        catch (IOException e)
        {
            System.out.println(username+" socket get closed");
      	    ServerUI.messageAppender.appendToMessagePane(ServerUI.logPane, ServerUI.dtf.format(LocalDateTime.now()) + " | ", Color.WHITE, true);
      	    ServerUI.messageAppender.appendToMessagePane(ServerUI.logPane, username+" socket get closed" + "\n\n", Color.WHITE, true);


        } catch (ParseException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        finally {

        System.out.println("thread "+username+" ended");
  	    ServerUI.messageAppender.appendToMessagePane(ServerUI.logPane, ServerUI.dtf.format(LocalDateTime.now()) + " | ", Color.WHITE, true);
  	    ServerUI.messageAppender.appendToMessagePane(ServerUI.logPane, "thread "+username+" ended" + "\n\n", Color.WHITE, true);
        
        try {

            if(this.isManager){

            JSONObject reply = new JSONObject();

            reply.put("Source","Server");
            reply.put("Goal","Close");
            reply.put("ObjectString", "There is something wrong in Manager " + username + " thread");

            PublishSubscribeSystem.getInstance().resetManager();

            PublishSubscribeSystem.getInstance().broadcastJSON(reply,this.username);


            LinkedBlockingQueue<ClientInfo> queue = PublishSubscribeSystem.getInstance().getQueue();


            Iterator<ClientInfo> listOfClients = queue.iterator();
            while (listOfClients.hasNext()) {
                ClientInfo current = listOfClients.next();
                Socket wait = current.getClient();
                if(!wait.isClosed()){
                    OutputStream out = wait.getOutputStream();
                    OutputStreamWriter woos =new OutputStreamWriter(out, "UTF8");
                    woos.write(reply.toJSONString()+"\n");
                    woos.flush();

                }
            }

            PublishSubscribeSystem.getInstance().disconnectServer();



        }
            if(!clientsocket.isClosed())
                clientsocket.close();
        }
        catch(IOException ex){

            System.out.println("incorrectly end thread");
        }

        }
    }


    public byte[] serialize(Object obj) throws IOException {
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        ObjectOutputStream os = new ObjectOutputStream(bao);
        os.writeObject(obj);
        return bao.toByteArray();
    }

    public Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream in = new ByteArrayInputStream(data);
        ObjectInputStream is = new ObjectInputStream(in);
        return is.readObject();
    }
}
