#!/usr/bin/env python

import mapnik

mapfile    = 'lhd.xml'
map_output = 'map.png'
m          = mapnik.Map(600, 300)

mapnik.load_map(m, mapfile)
bbox = mapnik.Envelope(mapnik.Coord(18.7, 47.8), mapnik.Coord(19, 48))
m.zoom_to_box(bbox) 

mapnik.render_to_file(m, map_output)

