package example.game;

import example.domain.game.*;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class Game {
    private static final Random rg = new Random();
    private final Map<Item, Location> items;
    private final Map<Player, Location> players;
    private final Map<Player, Integer> playerHealth;
    private final Map<Player, Integer> playerGold;
    private final Cave cave;

    public Map<Player, Integer> healths() {
        return Collections.unmodifiableMap(playerHealth);
    }

    public Map<Player, Integer> golds() {
        return Collections.unmodifiableMap(playerGold);
    }

    public Map<Player, Location> players() {
        return Collections.unmodifiableMap(players);
    }

    public Map<Item, Location> items() {
        return Collections.unmodifiableMap(items);
    }

    public Cave cave() {
        return cave;
    }

    public Game(Cave cave) {
        this.cave = cave;
        this.players = new HashMap<>();
        this.items = new HashMap<>();
        this.playerHealth = new HashMap<>();
        this.playerGold = new HashMap<>();
    }

    public void render() {
        final var tbl = new char[cave.columns() * cave.rows()];
        for (int row = 0; row < cave.rows(); row++) {
            for (int column = 0; column < cave.columns(); column++) {
                if (cave.rock(row, column)) {
                    tbl[row * cave.columns() + column] = 'X';
                } else {
                    tbl[row * cave.columns() + column] = ' ';
                }
            }
        }

        for (final var entry : players.entrySet()) {
            final var location = entry.getValue();
            tbl[location.row() * cave.columns() + location.column()] = switch (entry.getKey()) {
                case Player.HumanPlayer ignored -> 'P';
                case Player.Dragon ignored -> 'D';
            };
        }

        for (final var entry : items.entrySet()) {
            final var location = entry.getValue();
            tbl[location.row() * cave.columns() + location.column()] = switch (entry.getKey()) {
                case Item.Gold ignored -> 'G';
                case Item.Health ignored -> 'H';
            };
        }

        for (int row = 0; row < cave.rows(); row++) {
            for (int column = 0; column < cave.columns(); column++) {
                System.out.print(tbl[row * cave.columns() + column]);
            }
            System.out.println();
        }
    }

    public void add(Item entity, Supplier<Location> generateLocation) {
        for (; ; ) {
            final var location = generateLocation.get();

            if (items.containsValue(location)) {
                continue;
            }

            if (players.containsValue(location)) {
                continue;
            }

            items.put(entity, location);

            return;
        }
    }

    public void add(Player entity, Supplier<Location> generateLocation) {
        for (; ; ) {
            final var location = generateLocation.get();

            if (items.containsValue(location)) {
                continue;
            }

            if (players.containsValue(location)) {
                continue;
            }

            players.put(entity, location);
            if (entity instanceof Player.HumanPlayer player) {
                playerHealth.put(player, 100);
                playerGold.put(player, 0);
            }

            return;
        }
    }

    public Location randomLocation() {
        for (; ; ) {
            final var row = rg.nextInt(cave.rows());
            final var column = rg.nextInt(cave.columns());
            if (cave.rock(row, column)) {
                continue;
            }

            return new Location(row, column);
        }
    }

    public void step(Collection<Action> commands) {
        // make sure there is only one command per player
        final var filtered = commands.stream().collect(Collectors.groupingBy(Action::player)).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getFirst()));

        // apply commands to player locations
        final var moved = players.entrySet().stream()
                .map(entry -> {
                    final var action = filtered.get(entry.getKey());
                    if (action == null) {
                        return entry;
                    }

                    final var next = move(entry.getValue(), action);
                    if (cave.rock(next.row(), next.column())) {
                        return entry;
                    }

                    return Map.entry(entry.getKey(), next);
                })
                .collect(Collectors.groupingBy(Map.Entry::getValue));

        // fight and collect gems
        moved.forEach((key, value) -> fight(key, value.stream().map(Map.Entry::getKey).toList()));

        // update locations
        moved.forEach((location, entries) -> entries.forEach(entry -> players.put(entry.getKey(), entry.getValue())));
    }

    private void fight(Location location, List<Player> players) {
        if (players.isEmpty()) {
            return;
        }

        // "fight" - health of all players is reduced by half of health of the weakest one
        if (players.size() > 1) {
            final var optionalMinimum = players.stream().min((o1, o2) -> Integer.compare(playerHealth.get(o1), playerHealth.get(o2))).map(playerHealth::get);

            optionalMinimum.ifPresent(minimum -> players.forEach(player -> playerHealth.compute(player, (ignored, health) -> (2 * health - minimum + 1) / 2)));
        }

        final var optionalMaximum = players.stream().max((o1, o2) -> Integer.compare(playerHealth.get(o1), playerHealth.get(o2)));

        optionalMaximum.ifPresent(maximum -> {
            final var filtered = items.entrySet().stream().filter(entry -> entry.getValue().equals(location)).toList();

            // do something with non player entities, i.e. gold?
            items.entrySet().stream()
                    .filter(entry -> entry.getValue().equals(location))
                    .forEach(entry -> {
                        switch (entry.getKey()) {
                            case Item.Gold(int id, int value) -> {
                                playerGold.computeIfPresent(maximum, (ignored, current) -> current + value);
                            }

                            case Item.Health(int id, int value) -> {
                                playerHealth.computeIfPresent(maximum, (ignored, current) -> current + value);
                            }
                        }
                    });

            filtered.forEach(entry -> items.remove(entry.getKey()));
        });
    }

    private Location move(Location value, Action action) {
        return switch (action.direction()) {
            case Up -> new Location(value.row() - 1, value.column());
            case Down -> new Location(value.row() + 1, value.column());
            case Left -> new Location(value.row(), value.column() - 1);
            case Right -> new Location(value.row(), value.column() + 1);
        };
    }

    public Integer health(Player.HumanPlayer player) {
        return playerHealth.get(player);
    }

    public Integer gold(Player.HumanPlayer player) {
        return playerGold.get(player);
    }
}
