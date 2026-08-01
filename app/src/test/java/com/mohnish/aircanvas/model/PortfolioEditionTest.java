package com.mohnish.aircanvas.model;

import org.junit.Test;

import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class PortfolioEditionTest {
    @Test
    public void portfolioShowcaseContainsClientFacingSystems() {
        DesignDocument document = TemplateFactory.create(
                TemplateFactory.Template.PORTFOLIO_SHOWCASE
        );
        ProjectInsights insights = ProjectInsights.analyze(document);
        assertEquals("Portfolio Showcase", document.template);
        assertTrue(insights.objects() >= 12);
        assertTrue(insights.spatialObjects() >= 3);
        assertTrue(insights.connectors() >= 4);
    }

    @Test
    public void styleLockAlignAndDistributeRemainDeterministic() {
        DesignDocument document = new DesignDocument("Arrange");
        CanvasElement first = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 100, 100, 40, 40, "A"
        );
        CanvasElement second = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 260, 180, 40, 40, "B"
        );
        CanvasElement third = CanvasElement.node(
                CanvasElement.Type.RECTANGLE, 500, 260, 40, 40, "C"
        );
        document.elements.add(first);
        document.elements.add(second);
        document.elements.add(third);
        Set<String> selected = new LinkedHashSet<>(Set.of(
                first.id,
                second.id,
                third.id
        ));

        assertEquals(3, SelectionOperations.applyStyle(
                document,
                selected,
                StylePreset.NEON_GLASS
        ));
        assertEquals(3, SelectionOperations.align(
                document,
                selected,
                SelectionOperations.Alignment.TOP
        ));
        assertEquals(first.bounds().top, second.bounds().top, 0.001f);
        assertEquals(second.bounds().top, third.bounds().top, 0.001f);
        assertEquals(3, SelectionOperations.distribute(
                document,
                selected,
                SelectionOperations.Distribution.HORIZONTAL
        ));
        assertEquals(3, SelectionOperations.setLocked(document, selected, true));
        assertEquals(0, SelectionOperations.applyStyle(
                document,
                selected,
                StylePreset.WARNING
        ));
    }
}
