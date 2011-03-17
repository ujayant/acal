#!/usr/bin/env perl
#
# Quick hack to build a .pot file from an Android strings.xml file
#
# Copyright: Morphoss Ltd <http://www.morphoss.com/>
# Author: Andrew McMillan <andrew@mcmillan.net.nz>
# License: CC-0, Public Domain, GPL v2, GPL v3, Apache v2 or Simplified BSD
#          other licenses considered on request.
#

use strict;
use warnings;

# Stuff that should not be hard-coded, but is :-)
my $resources_dir = "../res";
my $strings_filename = "strings";

# First update the messages.po file
build_po_file($resources_dir ."/values/". $strings_filename . ".xml", "messages.po");

# Now merge into each translation
opendir(my $dh, $resources_dir);
while( my $fn = readdir($dh) ) {
  next unless( $fn =~ m{^values-} );
  printf( " -->%s<--\n", $fn );
  $fn =~ m{^values-(.*)$} && do {
    my $lang = $1;
    merge_po_file( $lang.".po", "messages.po" );
  };
}
closedir($dh);


sub build_po_file {
  my $filename = shift;
  my $outfile = shift;

  my @xgettext = ( "xgettext", "-L", "PO", "-o", $outfile, "-" );

  open( XMLFILE, "<", $filename );
  open( XGETTEXT, "|-", @xgettext );
  while( <XMLFILE> ) {
    m{<string .*?name="(.*?)".*?>(.*?)</string>} && do {
      my $msgid = $1;
      my $msgstr = $2;
      $msgstr =~ s{\\'}{'}g;
      printf( XGETTEXT 'msgid "%s"%s', $msgid, "\n" );
      printf( XGETTEXT 'msgstr "%s"%s', $msgstr, "\n\n" );
    };
    m{<!--(.*?)-->} && do {
      printf( XGETTEXT "#%s\n", $1 );
    };
  }
  close(XMLFILE);
  
}


sub merge_po_file {
  my $lang_file = shift;
  my $pot_file = shift;

  my @msgmerge = ( "msgmerge", "--update", "--previous", $lang_file, $pot_file );

  exec( @msgmerge );
}
