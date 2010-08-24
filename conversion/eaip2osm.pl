#!/usr/bin/perl
use strict;
use warnings;
use Getopt::Long;
unless (eval { require XML::Twig }) {
	print STDERR "XML::Twig is not installed please install it\n";
	if ( -e '/etc/debian_version' ) {
		print STDERR "looks like you're running debian, so you can try\n  apt-get install libxml-twig-perl\n";
	}	
	exit 1;
}

binmode STDOUT, ":utf8";
my ($aviation_area_type,$multiple_output,$helpmode);
GetOptions(
	"type=s"=>\$aviation_area_type,
	"multi"=>\$multiple_output,
	"help"=>\$helpmode,
);

$aviation_area_type = "danger" unless $aviation_area_type;

sub parse_eaip_file {
	my ($fn) = @_;
	my $x=XML::Twig->new();
	$x->parsefile($fn);
	
	my @sections = $x->descendants('e:Sub-section');
	foreach my $section ( @sections ) {
		my ($thead,undef,@rows) = $section->descendants('x:tr');
		foreach (@rows) {
			parse_eaip_enr_row($_->children);
		}
	}
}

sub parse_eaip_enr_row {
	my ($idname,$limits,$remarks) = @_;
	$idname = $idname->xml_string;
	my ($id,$name) = $idname =~ m#<x:strong(?:[^>]*)>(\w+)\s+/\s+(\w+)</x:strong>#;
	return unless defined $id;
	my (undef,$polystr) = split(m#<x:br(?:[^>]*)>#,$idname,2);
	if ($polystr=~ m/A circle/) {
		#circle
	} else {
		#poly
		if ($multiple_output) {
			print qq!<?xml version='1.0' encoding='UTF-8'?>\n<osm version='0.6'>\n!;
		}
		my $c = 0;
		for my $pts (split(m/\s*-\s*/,$polystr)) {
			my ($lat,$lon) = map {dms2decimal($_)} split(' ',$pts);
			my $i = id2str($id."_".$c);
			print qq!<node id="$i" lat="$lat" lon="$lon"/>\n!;
			$c++;
		}
		print qq!<way id="$id" visible="true">\n!;
		for (0..$c-1,0) {
			my $i = id2str($id."_".$_);
			print qq!\t<nd ref="$i"/>\n!;
		}
		print qq!	<tag k="name" v="$id"/>
	<tag k="area" v="yes"/>
	<tag k="aviation_area" v="$aviation_area_type"/>\n!;
		print "</way>\n";
		if ($multiple_output) {
			print "</osm>\n";
		}
	}
}

sub dms2decimal {
	my $_ = shift;
	$_ =~ /(\d\d)(\d\d)(\d\d)([^\d])/;
	my $dir = $4;
	my $coord = $1 + ($2*60 + $3)/3600;
	if ($dir eq 'S' || $dir eq 'W') {
		$coord *= -1;
	}
	return $coord;
}

sub id2str {
	my $id = shift;
	return join('',map {ord($_) - ord '0'} split(//,$id));
}

if ($helpmode || !@ARGV) {
	print "\nUsage: $0 [OPTIONS] FILE [FILE]...\n\n";
	print "OPTIONS\n";
	print " -type	specify aviation_area in output xmls (default: danger)\n";
	print " -multi	save multiple .osm files instead of one big output to STDOUT (not working yet)\n";
	print "--help	display this help and exit\n";
	print "\n";
	print "Check for latest version and report bugs at http://code.google.com/p/openaviationmap/\n\n";
	exit 1;
}


die "no multiple output support yet ;)\n" if $multiple_output;

print qq!<?xml version='1.0' encoding='UTF-8'?>\n<osm version='0.6'>\n! unless $multiple_output;

for my $fn (@ARGV) {
	parse_eaip_file($fn);
}

print "</osm>\n" unless $multiple_output;

