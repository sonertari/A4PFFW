/*
 * Copyright (C) 2017 Soner Tari
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

public class GraphsIfs extends GraphsBase {

    private Bitmap bmpIntIf;
    private Bitmap bmpExtIf;
    private Bitmap bmpLbIf;

    private ImageView ivIntIf;
    private ImageView ivExtIf;
    private ImageView ivLbIf;

    @Override
    public void init(LayoutInflater inflater, ViewGroup container) {
        view = inflater.inflate(R.layout.graphs_ifs, container, false);

        mLayout = "ifs";

        ivIntIf = (ImageView) view.findViewById(R.id.imageViewIntIf);
        ivIntIf.setOnClickListener(onViewClick);

        ivExtIf = (ImageView) view.findViewById(R.id.imageViewExtIf);
        ivExtIf.setOnClickListener(onViewClick);

        ivLbIf = (ImageView) view.findViewById(R.id.imageViewLbIf);
        ivLbIf.setOnClickListener(onViewClick);

        if (cache.graphsIfs == null) {
            cache.graphsIfs = new GraphsIfsCache();
        }
        mModuleCache = cache.graphsIfs;
    }

    @Override
    public void saveImages() {
        ((GraphsIfsCache) mModuleCache).bmpIntIf = bmpIntIf;
        ((GraphsIfsCache) mModuleCache).bmpExtIf = bmpExtIf;
        ((GraphsIfsCache) mModuleCache).bmpLbIf = bmpLbIf;
    }

    @Override
    public void restoreImages() {
        bmpIntIf = ((GraphsIfsCache) mModuleCache).bmpIntIf;
        bmpExtIf = ((GraphsIfsCache) mModuleCache).bmpExtIf;
        bmpLbIf = ((GraphsIfsCache) mModuleCache).bmpLbIf;
    }

    @Override
    public void setBitmap(String title, Bitmap bmp) {
        if (title.compareTo("Internal Interface") == 0) {
            bmpIntIf = bmp;
        } else if (title.compareTo("External Interface") == 0) {
            bmpExtIf = bmp;
        } else if (title.compareTo("Loopback Interface") == 0) {
            bmpLbIf = bmp;
        }
    }

    @Override
    public Bitmap getBitmap(View view) {
        int id = view.getId();

        if (id == R.id.imageViewIntIf) {
            return bmpIntIf;
        } else if (id == R.id.imageViewExtIf) {
            return bmpExtIf;
        } else {
            return bmpLbIf;
        }
    }

    @Override
    public void updateImages() {
        /// @todo Check why setMaxHeight() does not work
        //ivExtIf.setMaxHeight(Math.round(ivExtIf.getMeasuredWidth() / 3f));
        ivExtIf.setImageBitmap(bmpExtIf);
        ivIntIf.setImageBitmap(bmpIntIf);
        ivLbIf.setImageBitmap(bmpLbIf);
    }
}

class GraphsIfsCache extends GraphsCache {
    Bitmap bmpIntIf;
    Bitmap bmpExtIf;
    Bitmap bmpLbIf;
}
