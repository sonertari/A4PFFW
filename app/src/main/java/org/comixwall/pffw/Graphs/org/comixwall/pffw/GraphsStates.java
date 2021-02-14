/*
 * Copyright (C) 2017-2021 Soner Tari
 *
 * This file is part of PFFW.
 *
 * PFFW is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * PFFW is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with PFFW.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.comixwall.pffw;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import static org.comixwall.pffw.MainActivity.cache;

public class GraphsStates extends GraphsBase {

    private Bitmap bmpStates;
    private Bitmap bmpSearchesPkts;

    private ImageView ivStates;
    private ImageView ivSearchesPkts;

    @Override
    public void init(LayoutInflater inflater, ViewGroup container) {
        view = inflater.inflate(R.layout.graphs_states, container, false);

        mLayout = "states";

        ivStates = view.findViewById(R.id.imageViewStates);
        ivStates.setOnClickListener(onViewClick);

        ivSearchesPkts = view.findViewById(R.id.imageViewSearchesPkts);
        ivSearchesPkts.setOnClickListener(onViewClick);

        if (cache.graphsStates == null) {
            cache.graphsStates = new GraphsStatesCache();
        }
        mModuleCache = cache.graphsStates;
    }

    @Override
    public void saveImages() {
        ((GraphsStatesCache) mModuleCache).bmpStates = bmpStates;
        ((GraphsStatesCache) mModuleCache).bmpSearchesPkts = bmpSearchesPkts;
    }

    @Override
    public void restoreImages() {
        bmpStates = ((GraphsStatesCache) mModuleCache).bmpStates;
        bmpSearchesPkts = ((GraphsStatesCache) mModuleCache).bmpSearchesPkts;
    }

    @Override
    public void setBitmap(String title, Bitmap bmp) {
        if (title.equals("State Statistics")) {
            bmpStates = bmp;
        } else if (title.equals("State Searches vs Packets")) {
            bmpSearchesPkts = bmp;
        }
    }

    @Override
    public Bitmap getBitmap(View view) {
        if (view.getId() == R.id.imageViewStates) {
            return bmpStates;
        } else {
            return bmpSearchesPkts;
        }
    }

    @Override
    public void updateImages() {
        ivStates.setImageBitmap(bmpStates);
        ivSearchesPkts.setImageBitmap(bmpSearchesPkts);
    }
}

class GraphsStatesCache extends GraphsCache {
    Bitmap bmpStates;
    Bitmap bmpSearchesPkts;
}
