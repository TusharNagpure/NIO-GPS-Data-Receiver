package com.example.websocket;

public class Device {
    private String name;
    private String host;
    private int port;
    private String username;
    private String password;
    private String TargetDirectory;

    public Device(String name, String host, int port,
                  String username, String password,
                  String TargetDirectory) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.TargetDirectory = TargetDirectory;
    }

    // Getters
    public String getName() { return name; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getTargetDirectory() { return TargetDirectory; }
}
