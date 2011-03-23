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
my $messages_filename = "messages.po";

if ( $ARGV[0] eq "extract" ) {
  # Update/extract the strings for the messages.po file
  extract_po_file($resources_dir ."/values/". $strings_filename . ".xml", $messages_filename);
}
elsif ( $ARGV[0] eq "build" ) {
  # From the language po files build the various strings files.
  build_strings_files($messages_filename, $strings_filename );
}


=item
Extracts the strings from an Android strings.xml into a messages.po file
=cut
sub extract_po_file {
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


=item
Finds the translated .po files in a directory and uses them to construct a new strings.xml file
for each one by merging the details from the original strings.xml with the translated strings
from the la_NG.po file.
=cut
sub build_strings_files {
  my $template_file = shift;
  my $stringsfile = shift;

  # Now merge into each translation
  opendir(my $dh, ".");
  while( my $fn = readdir($dh) ) {
    if( $fn =~ m{^([a-z]{2}(_[A-Z]{2})?)\.po$} ) {
      my $lang = $1;
      printf( "Building language: %s\n", $lang );
      my $strings = get_translated_strings($fn);
      merge_into_xml( $lang, $strings );
    }
  }
  closedir($dh);
}


=item
=cut
sub get_translated_strings {
  my $filename = shift;

  my $strings = {};
  open( TRANSLATED, "<", $filename );

  my $msgid = undef;
  while( <TRANSLATED> ) {
    next if ( /^\s*#/ );

    if ( /^\s*msgid \"(.*)"\r?\n?$/ ) {
      $msgid = $1;
      $strings->{$msgid} = "";
    }
    elsif ( defined($msgid) ) {
      /^\s*(msgstr )?"(.*)"\r?\n?$/ && do {
        $strings->{$msgid} .= $2;
      };
    }
  }

  close(TRANSLATED);

  foreach my $s ( keys %{$strings} ) {
    printf( STDOUT "<string name=\"%s\">%s</string>\n", $s, $strings->{$s} );
  }

  return $strings;
}


=item
=cut
sub merge_into_xml {
  my $lang = shift;
  my $strings = shift;

  my $in_filename = sprintf( '%s/values/%s.xml', $resources_dir, $strings_filename);
  my $out_filename = sprintf( '%s/values-%s/%s.xml', $resources_dir, $lang, $strings_filename);
  open( XMLIN, "<", $in_filename );
  open( XMLOUT, ">", $out_filename );

  while( <XMLIN> ) {
    if ( ! m{<!--} && m{<string (.*?)name="(.*?)"(.*?)>(.*?)</string>} ) {
      my $msgid = $2;
      my $msgstr = $4;
      $msgstr =~ s{\\'}{'}g;
      next if ( ! defined($strings->{$msgid}) || $msgstr eq $strings->{$msgid} );
      $strings->{$msgid} =~ s{"}{&quot;}g;
      $strings->{$msgid} =~ s{['\\]}{\\$1}g;
      printf( XMLOUT '<string %sname="%s"%s>%s</string>%s', $1, $msgid, $3, $strings->{$msgid}, "\n" );
    }
    else {
      print XMLOUT;
    }
  }
  
  close(XMLIN);
  close(XMLOUT);
}

