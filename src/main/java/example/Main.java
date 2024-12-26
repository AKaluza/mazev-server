package example;

import example.domain.game.Cave;
import example.domain.game.DrunkenCave;
import example.domain.game.SimpleCave;
import example.game.Game;
import example.server.Server;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
//        final var cave = new SimpleCave(60, 160);
        final var cave = new DrunkenCave(60, 160);
        final var game = new Game(cave);
        final var server = new Server(game);
        server.start(8080);
    }
}
