package com.gkd.jgraphx;

import java.awt.Color;
import java.awt.Font;

import javax.swing.BorderFactory;
import javax.swing.CellRendererPane;
import javax.swing.JComponent;
import javax.swing.JLabel;

import com.mxgraph.model.mxCell;
import com.mxgraph.swing.mxGraphComponent;
import com.mxgraph.swing.view.mxInteractiveCanvas;
import com.mxgraph.view.mxCellState;

public class JGraphxCanvas extends mxInteractiveCanvas {
	protected CellRendererPane rendererPane = new CellRendererPane();

	protected JLabel jLabel = new JLabel();

	protected mxGraphComponent graphComponent;

	Color borderColor = new Color(0, 102, 255);
	Color backgroundcolor = new Color(195, 219, 255);

	public JGraphxCanvas(mxGraphComponent graphComponent) {
		this.graphComponent = graphComponent;

		jLabel.setBorder(BorderFactory.createLineBorder(borderColor));
		jLabel.setHorizontalAlignment(JLabel.CENTER);
		jLabel.setBackground(backgroundcolor);
		jLabel.setOpaque(true);
		jLabel.setFont(new Font("arial", Font.PLAIN, 10));
	}

	public void drawVertex(mxCellState state) {
		mxCell cell = (mxCell) state.getCell();
		JComponent value = (JComponent) cell.getValue();
		rendererPane.paintComponent(g, value, graphComponent, (int) state.getX() + translate.x, (int) state.getY() + translate.y, (int) state.getWidth(), (int) state.getHeight(),
				true);
	}

	public void drawBackground() {
		g.setColor(Color.red);
		g.fillRect(0, 0, graphComponent.getWidth(), graphComponent.getHeight());
	}

}