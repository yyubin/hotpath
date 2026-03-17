package org.yyubin.hotpath.model.flamegraph;

import org.yyubin.hotpath.model.Finding;

import java.util.List;

public record FlameGraphResult(
        FlameGraphMeta    meta,
        FlameNode         root,
        FlameGraphSummary summary,
        List<Finding>     findings
) {}
