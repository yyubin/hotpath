package org.yyubin.hotpath;

import picocli.CommandLine;

public class Main {
    public static void main(String[] args) {
        int exitCode = new CommandLine(new HotpathCommand()).execute(args);
        System.exit(exitCode);
    }
}
