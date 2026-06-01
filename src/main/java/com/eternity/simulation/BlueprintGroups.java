package com.eternity.simulation;

import java.util.Set;

public final class BlueprintGroups {
    private BlueprintGroups() {}

    public static final Set<String> GROUP1 = Set.of(
        "cyclic:harvester", "cyclic:forester", "cyclic:sprinkler",
        "cyclic:collector", "cyclic:collector_fluid", "cyclic:placer",
        "cyclic:dropper", "cyclic:fan", "solarflux:sp_1", "solarflux:sp_2"
    );
    public static final Set<String> GROUP2 = Set.of(
        "cyclic:miner", "cyclic:breaker", "cyclic:crafter",
        "cyclic:user", "solarflux:sp_3", "solarflux:sp_4", "solarflux:sp_5"
    );
    public static final Set<String> GROUP3 = Set.of(
        "cyclic:enchanter", "cyclic:disenchanter", "cyclic:structure",
        "solarflux:sp_6", "solarflux:sp_7", "solarflux:sp_8"
    );
}
