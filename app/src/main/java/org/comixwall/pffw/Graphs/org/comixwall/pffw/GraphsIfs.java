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

        ivIntIf = view.findViewById(R.id.imageViewIntIf);
        ivIntIf.setOnClickListener(onViewClick);

        ivExtIf = view.findViewById(R.id.imageViewExtIf);
        ivExtIf.setOnClickListener(onViewClick);

        ivLbIf = view.findViewById(R.id.imageViewLbIf);
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
        switch (title) {
            case "Internal Interface":
                bmpIntIf = bmp;
                break;
            case "External Interface":
                bmpExtIf = bmp;
                break;
            case "Loopback Interface":
                bmpLbIf = bmp;
                break;
        }
    }

    @Override
    public Bitmap getBitmap(View view) {
        switch (view.getId()) {
            case R.id.imageViewIntIf:
                return bmpIntIf;
            case R.id.imageViewExtIf:
                return bmpExtIf;
            default:
                return bmpLbIf;
        }
    }

    @Override
    public void updateImages() {
        // TODO: Check why setMaxHeight() does not work
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
