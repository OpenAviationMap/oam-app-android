<Style name="OpenAviationMap">
    <Rule>
        <Filter>[aviation_area] = 'danger'</Filter>
        <LinePatternSymbolizer width="20" height="20" type="png"
                               file="&symbols;/danger_area_edge.png"
                               />
        <TextSymbolizer name="name"
                        face_name="DejaVu Sans Book"
                        size="10"
                        text_convert="toupper"
                        vertical_alignment="bottom"
                        fill="#ff0000"/>
    </Rule>
</Style>


<Layer name="lhd" status="on" srs="&osm2pgsql_projection;">
    <StyleName>OpenAviationMap</StyleName>
    <Datasource>
        <Parameter name="table">
        (select way,name,aviation_area from &prefix;_polygon where aviation_area IS NOT NULL) as aviation_area
        </Parameter>
    </Datasource>
</Layer>

