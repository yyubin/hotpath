package org.yyubin.hotpath.model.flamegraph;

public record HotFrame(
        String name,
        long   self,
        long   total,
        double selfPct,
        double totalPct
) {}
